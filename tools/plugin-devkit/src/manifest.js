import fs from 'node:fs';
import path from 'node:path';
import {
  CAPABILITIES,
  CONFIG_FIELD_TYPES,
  LIMITS,
  PLUGIN_API_VERSION
} from './spec.js';
import {
  directorySize,
  isInside,
  isPlainObject,
  normalizeRelativePath,
  pathExists,
  readJson
} from './fs-utils.js';
import { Report } from './report.js';

const PLUGIN_ID_RE = /^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$/;

export async function loadManifest(pluginRoot) {
  const manifestPath = path.join(pluginRoot, 'manifest.json');
  if (!(await pathExists(manifestPath))) {
    throw new Error(`manifest.json not found in ${pluginRoot}`);
  }
  return readJson(manifestPath);
}

export async function validatePluginRoot(pluginRoot) {
  const report = new Report();
  const absoluteRoot = path.resolve(pluginRoot);
  const manifestPath = path.join(absoluteRoot, 'manifest.json');

  if (!(await pathExists(absoluteRoot))) {
    report.error('Plugin path does not exist', absoluteRoot);
    return { report, manifest: null, root: absoluteRoot };
  }

  const stat = await fs.promises.stat(absoluteRoot);
  if (!stat.isDirectory()) {
    report.error('Only plugin directories are supported in this devkit version', absoluteRoot);
    return { report, manifest: null, root: absoluteRoot };
  }

  if (!(await pathExists(manifestPath))) {
    report.error('manifest.json is missing');
    return { report, manifest: null, root: absoluteRoot };
  }

  const manifestSize = (await fs.promises.stat(manifestPath)).size;
  if (manifestSize > LIMITS.manifestBytes) {
    report.error('manifest.json exceeds size limit', `${manifestSize} bytes`);
  } else {
    report.pass('manifest.json exists');
  }

  let manifest = null;
  try {
    manifest = await readJson(manifestPath);
  } catch (error) {
    report.error(error.message);
    return { report, manifest: null, root: absoluteRoot };
  }

  validateManifestShape(manifest, report);
  await validateFiles(absoluteRoot, manifest, report);

  const totalSize = await directorySize(absoluteRoot);
  if (totalSize > LIMITS.singlePluginBytes) {
    report.warn('Plugin directory exceeds app single-plugin install limit', `${totalSize} bytes`);
  } else {
    report.pass('Plugin directory size is within app install limit', `${totalSize} bytes`);
  }

  return { report, manifest, root: absoluteRoot };
}

export function validateManifestShape(manifest, report = new Report()) {
  if (!isPlainObject(manifest)) {
    report.error('manifest.json must be a JSON object');
    return report;
  }

  requireString(manifest, report, 'id');
  if (typeof manifest.id === 'string') {
    if (!PLUGIN_ID_RE.test(manifest.id)) {
      report.error('id must use reverse-domain format', manifest.id);
    } else {
      report.pass('id is valid', manifest.id);
    }
  }

  requireString(manifest, report, 'name');
  requireString(manifest, report, 'versionName');
  requirePositiveInt(manifest, report, 'versionCode');
  requirePositiveInt(manifest, report, 'apiVersion');
  if (manifest.apiVersion !== undefined) {
    if (manifest.apiVersion !== PLUGIN_API_VERSION) {
      report.error('apiVersion does not match Lyrico plugin API', `expected ${PLUGIN_API_VERSION}, got ${manifest.apiVersion}`);
    } else {
      report.pass('apiVersion matches Lyrico plugin API', String(manifest.apiVersion));
    }
  }

  optionalString(manifest, report, 'author');
  optionalString(manifest, report, 'description');
  optionalString(manifest, report, 'entry');
  optionalNullableString(manifest, report, 'icon');

  validateStringArray(manifest, report, 'includeDirs');
  validateEnumArray(manifest, report, 'capabilities', CAPABILITIES);

  if (Array.isArray(manifest.capabilities) && manifest.capabilities.length > 0 && !manifest.capabilities.includes('searchSongs')) {
    report.error('source plugins must support searchSongs when capabilities are declared');
  }

  validateConfigFields(manifest.configFields, report);
  warnRemovedManifestField(manifest, report, 'requiredHostApis');
  warnRemovedManifestField(manifest, report, 'returnedFields');
  warnRemovedManifestField(manifest, report, 'metadataFields');

  return report;
}

async function validateFiles(root, manifest, report) {
  const entry = normalizeRelativePath(manifest.entry ?? 'source.js');
  if (!entry || !entry.endsWith('.js')) {
    report.error('entry must be a safe relative .js path', manifest.entry ?? 'source.js');
  } else {
    const entryPath = path.resolve(root, entry);
    if (!isInside(root, entryPath)) {
      report.error('entry escapes plugin root', entry);
    } else if (!(await pathExists(entryPath))) {
      report.error('entry file does not exist', entry);
    } else {
      const size = (await fs.promises.stat(entryPath)).size;
      if (size > LIMITS.entryBytes) {
        report.error('entry file exceeds size limit', `${size} bytes`);
      } else {
        report.pass('entry file exists', entry);
      }
    }
  }

  for (const dir of manifest.includeDirs ?? []) {
    const normalized = normalizeRelativePath(dir);
    if (!normalized || normalized === '.') {
      report.error('includeDirs entry must be a safe relative directory', dir);
      continue;
    }
    const dirPath = path.resolve(root, normalized);
    if (!isInside(root, dirPath)) {
      report.error('includeDir escapes plugin root', dir);
      continue;
    }
    if (!(await pathExists(dirPath))) {
      report.error('includeDir does not exist', dir);
      continue;
    }
    const stat = await fs.promises.stat(dirPath);
    if (!stat.isDirectory()) {
      report.error('includeDir is not a directory', dir);
    } else {
      report.pass('includeDir exists', dir);
    }
  }

  if (manifest.icon != null && manifest.icon !== '') {
    const icon = normalizeRelativePath(manifest.icon);
    if (!icon || !/\.(png|jpe?g|webp)$/i.test(icon)) {
      report.error('icon must be a safe relative png/jpg/jpeg/webp path', manifest.icon);
    } else {
      const iconPath = path.resolve(root, icon);
      if (!isInside(root, iconPath) || !(await pathExists(iconPath))) {
        report.error('icon file does not exist inside plugin root', icon);
      } else {
        report.pass('icon file exists', icon);
      }
    }
  }
}

function validateConfigFields(fields, report) {
  if (fields === undefined) return;
  if (!Array.isArray(fields)) {
    report.error('configFields must be an array');
    return;
  }

  const keys = new Set();
  fields.forEach((field, index) => {
    const prefix = `configFields[${index}]`;
    if (!isPlainObject(field)) {
      report.error(`${prefix} must be an object`);
      return;
    }
    validateKeyedObject(field, report, prefix, keys);
    requireString(field, report, 'title', prefix);
    if (!CONFIG_FIELD_TYPES.has(field.type)) {
      report.error(`${prefix}.type is invalid`, field.type);
    }
    if (field.options !== undefined) {
      if (!Array.isArray(field.options)) {
        report.error(`${prefix}.options must be an array`);
      } else {
        field.options.forEach((option, optionIndex) => {
          if (!isPlainObject(option) || typeof option.value !== 'string' || typeof option.label !== 'string') {
            report.error(`${prefix}.options[${optionIndex}] must have string value and label`);
          }
        });
      }
    }
  });
}

function validateKeyedObject(field, report, prefix, keys) {
  if (typeof field.key !== 'string' || field.key.trim() === '') {
    report.error(`${prefix}.key is required`);
  } else if (keys.has(field.key)) {
    report.error(`${prefix}.key is duplicated`, field.key);
  } else {
    keys.add(field.key);
  }
}

function warnRemovedManifestField(manifest, report, fieldName) {
  if (manifest[fieldName] !== undefined) {
    report.warn(`${fieldName} is ignored by the current plugin protocol`);
  }
}

function requireString(obj, report, key, prefix = '') {
  const name = prefix ? `${prefix}.${key}` : key;
  if (typeof obj[key] !== 'string' || obj[key].trim() === '') {
    report.error(`${name} is required and must be a non-empty string`);
  }
}

function optionalString(obj, report, key) {
  if (obj[key] !== undefined && typeof obj[key] !== 'string') {
    report.error(`${key} must be a string`);
  }
}

function optionalNullableString(obj, report, key) {
  if (obj[key] !== undefined && obj[key] !== null && typeof obj[key] !== 'string') {
    report.error(`${key} must be a string or null`);
  }
}

function requirePositiveInt(obj, report, key) {
  if (!Number.isInteger(obj[key]) || obj[key] < 1) {
    report.error(`${key} is required and must be an integer >= 1`);
  }
}

function validateStringArray(obj, report, key) {
  if (obj[key] === undefined) return;
  if (!Array.isArray(obj[key]) || obj[key].some(value => typeof value !== 'string')) {
    report.error(`${key} must be a string array`);
  }
}

function validateEnumArray(obj, report, key, allowed) {
  if (obj[key] === undefined) return;
  if (!Array.isArray(obj[key])) {
    report.error(`${key} must be an array`);
    return;
  }
  const invalid = obj[key].filter(value => typeof value !== 'string' || !allowed.has(value));
  if (invalid.length > 0) {
    report.error(`${key} contains unsupported values`, invalid);
  }
}
