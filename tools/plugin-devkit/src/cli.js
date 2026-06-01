import fs from 'node:fs';
import path from 'node:path';
import { validatePluginRoot } from './manifest.js';
import { printReport } from './report.js';
import { loadPlugin } from './plugin-loader.js';
import { createRuntime } from './runtime.js';
import { validateFunctionResult } from './result-parser.js';
import { writeZipFromDirectory } from './zip.js';

const COMMANDS = new Set(['validate', 'inspect', 'test', 'pack']);

main().catch(error => {
  console.error(`ERROR: ${error.message}`);
  if (process.env.DEBUG) console.error(error.stack);
  process.exitCode = 1;
});

async function main() {
  const [command, ...rest] = process.argv.slice(2);
  if (!command || command === '--help' || command === '-h') {
    printHelp();
    return;
  }
  if (!COMMANDS.has(command)) {
    throw new Error(`Unknown command: ${command}`);
  }

  if (command === 'validate') return commandValidate(rest);
  if (command === 'inspect') return commandInspect(rest);
  if (command === 'test') return commandTest(rest);
  if (command === 'pack') return commandPack(rest);
}

async function commandValidate(args) {
  const { positionals, flags } = parseArgs(args);
  const pluginPath = positionals[0];
  if (!pluginPath) throw new Error('Usage: lyrico-plugin validate <plugin-dir> [--json]');

  const result = await validatePluginRoot(pluginPath);
  if (flags.json) {
    console.log(JSON.stringify(result.report.toJSON(), null, 2));
  } else {
    printReport(result.report);
  }
  if (!result.report.ok) process.exitCode = 1;
}

async function commandInspect(args) {
  const { positionals, flags } = parseArgs(args);
  const pluginPath = positionals[0];
  if (!pluginPath) throw new Error('Usage: lyrico-plugin inspect <plugin-dir> [--json]');

  const validation = await validatePluginRoot(pluginPath);
  if (!validation.report.ok) {
    printReport(validation.report);
    process.exitCode = 1;
    return;
  }
  const plugin = await loadPlugin(pluginPath);
  const summary = {
    id: plugin.manifest.id,
    name: plugin.manifest.name,
    versionCode: plugin.manifest.versionCode,
    versionName: plugin.manifest.versionName,
    apiVersion: plugin.manifest.apiVersion,
    capabilities: effectiveCapabilities(plugin.manifest),
    configFields: (plugin.manifest.configFields ?? []).map(field => ({ key: field.key, type: field.type, required: !!field.required })),
    files: plugin.files
  };

  if (flags.json) {
    console.log(JSON.stringify(summary, null, 2));
  } else {
    console.log(`${summary.name} (${summary.id})`);
    console.log(`Version: ${summary.versionName} (${summary.versionCode})`);
    console.log(`API: ${summary.apiVersion}`);
    console.log(`Capabilities: ${summary.capabilities.join(', ')}`);
    console.log(`Config fields: ${summary.configFields.length ? summary.configFields.map(field => `${field.key}:${field.type}`).join(', ') : '(none)'}`);
    console.log('Script load order:');
    summary.files.forEach(file => console.log(`  - ${file}`));
  }
}

async function commandTest(args) {
  const { positionals, flags } = parseArgs(args);
  const pluginPath = positionals[0];
  const functionName = positionals[1] ?? 'searchSongs';
  if (!pluginPath || !['searchSongs', 'getLyrics', 'searchCovers'].includes(functionName)) {
    throw new Error('Usage: lyrico-plugin test <plugin-dir> <searchSongs|getLyrics|searchCovers> [options]');
  }

  const validation = await validatePluginRoot(pluginPath);
  if (!validation.report.ok) {
    printReport(validation.report);
    process.exitCode = 1;
    return;
  }

  const plugin = await loadPlugin(pluginPath);
  const config = await loadConfig(flags.config);
  const request = await buildRequest(functionName, flags, config);
  const runtime = await createRuntime(plugin, { echoLogs: flags.logs });
  const callResult = await runtime.call(functionName, request);
  const checked = validateFunctionResult(functionName, callResult.raw, plugin);

  const output = {
    plugin: { id: plugin.manifest.id, name: plugin.manifest.name },
    functionName,
    durationMs: callResult.durationMs,
    request,
    raw: callResult.raw,
    parsed: checked.parsed,
    warnings: checked.warnings,
    errors: checked.errors,
    logs: callResult.logs
  };

  if (flags.json) {
    console.log(JSON.stringify(output, null, 2));
  } else {
    printTestOutput(output);
  }
  if (checked.errors.length > 0) process.exitCode = 1;
}

async function commandPack(args) {
  const { positionals, flags } = parseArgs(args);
  const pluginPath = positionals[0];
  if (!pluginPath) throw new Error('Usage: lyrico-plugin pack <plugin-dir> [--out <zip-file>]');

  const validation = await validatePluginRoot(pluginPath);
  if (!validation.report.ok) {
    printReport(validation.report);
    process.exitCode = 1;
    return;
  }

  const manifest = validation.manifest;
  const out = flags.out
    ? path.resolve(String(flags.out))
    : path.resolve(pluginPath, '..', 'dist', `${manifest.id}-${manifest.versionName}.zip`);
  const result = await writeZipFromDirectory(path.resolve(pluginPath), out);
  console.log(`Packed ${result.entries} files`);
  console.log(result.outputPath);
}

async function buildRequest(functionName, flags, config) {
  if (flags.case) {
    const testCase = JSON.parse(await fs.promises.readFile(path.resolve(String(flags.case)), 'utf8'));
    if (testCase[functionName]) {
      return { ...testCase[functionName], config: testCase.config ?? config };
    }
  }

  if (functionName === 'searchSongs') {
    return {
      keyword: String(flags.keyword ?? ''),
      page: numberFlag(flags.page, 1),
      pageSize: numberFlag(flags.pageSize, 20),
      separator: String(flags.separator ?? '/'),
      config
    };
  }
  if (functionName === 'searchCovers') {
    return {
      keyword: String(flags.keyword ?? ''),
      pageSize: numberFlag(flags.pageSize, 5),
      config
    };
  }

  let song = null;
  if (flags.song) {
    song = JSON.parse(await fs.promises.readFile(path.resolve(String(flags.song)), 'utf8'));
  } else if (flags.songJson) {
    song = JSON.parse(String(flags.songJson));
  }
  if (!song) {
    throw new Error('getLyrics requires --song <song.json> or --song-json <json>');
  }
  return { song, config };
}

async function loadConfig(configPath) {
  if (!configPath) return {};
  const parsed = JSON.parse(await fs.promises.readFile(path.resolve(String(configPath)), 'utf8'));
  return parsed.config ?? parsed;
}

function printTestOutput(output) {
  console.log(`Plugin: ${output.plugin.name} (${output.plugin.id})`);
  console.log(`Function: ${output.functionName}`);
  console.log(`Status: ${output.errors.length ? 'FAILED' : 'OK'}`);
  console.log(`Duration: ${output.durationMs}ms`);
  console.log('');
  if (output.logs.length) {
    console.log('Logs:');
    output.logs.forEach(log => console.log(`  [${log.level}] ${log.tag}: ${log.message}`));
    console.log('');
  }
  if (output.warnings.length) {
    console.log('Warnings:');
    output.warnings.forEach(warning => console.log(`  - ${warning}`));
    console.log('');
  }
  if (output.errors.length) {
    console.log('Errors:');
    output.errors.forEach(error => console.log(`  - ${error}`));
    console.log('');
  }
  console.log('Raw:');
  console.log(output.raw);
  console.log('');
  console.log('Parsed:');
  console.log(JSON.stringify(output.parsed, null, 2));
}

function parseArgs(args) {
  const positionals = [];
  const flags = {};
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (!arg.startsWith('--')) {
      positionals.push(arg);
      continue;
    }
    const rawKey = arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
    const next = args[i + 1];
    if (next === undefined || next.startsWith('--')) {
      flags[key] = true;
    } else {
      flags[key] = next;
      i++;
    }
  }
  return { positionals, flags };
}

function effectiveCapabilities(manifest) {
  return manifest.capabilities?.length ? manifest.capabilities : ['searchSongs'];
}

function numberFlag(value, fallback) {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function printHelp() {
  console.log(`Lyrico plugin devkit

Usage:
  lyrico-plugin validate <plugin-dir> [--json]
  lyrico-plugin inspect <plugin-dir> [--json]
  lyrico-plugin test <plugin-dir> <searchSongs|getLyrics|searchCovers> [options]
  lyrico-plugin pack <plugin-dir> [--out <zip-file>]

Test options:
  --keyword <text>          Keyword for searchSongs/searchCovers
  --page <n>                Page for searchSongs
  --page-size <n>           Page size
  --separator <text>        Artist separator for searchSongs
  --config <config.json>    Config map or { "config": { ... } }
  --case <case.json>        Test case file
  --song <song.json>        Song object for getLyrics
  --song-json <json>        Inline song object for getLyrics
  --logs                    Echo plugin logs while running
  --json                    Print machine-readable JSON
`);
}
