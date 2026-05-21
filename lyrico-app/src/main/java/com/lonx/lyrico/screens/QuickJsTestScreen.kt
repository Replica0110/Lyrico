package com.lonx.lyrico.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.R
import com.lonx.lyrico.plugin.runtime.QuickJsRuntime
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
@Destination<RootGraph>(route = "quickjs_test")
fun QuickJsTestScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    var evalScript by rememberSaveable {
        mutableStateOf(DEFAULT_EVAL_SCRIPT)
    }

    var evalResult by remember { mutableStateOf<String?>(null) }
    var callResult by remember { mutableStateOf<String?>(null) }
    var timeoutResult by remember { mutableStateOf<String?>(null) }
    var memoryResult by remember { mutableStateOf<String?>(null) }
    var isRunning by remember { mutableStateOf(false) }

    val evalExamples = remember {
        listOf(
            ScriptExample(
                title = "基础对象处理",
                script = """
                    const input = {
                      title: "  Numb  ",
                      artists: ["Linkin Park", "Jay-Z"],
                      album: "Collision Course",
                      durationMs: 185000
                    };

                    JSON.stringify({
                      title: input.title.trim(),
                      artist: input.artists.join(" / "),
                      album: input.album,
                      durationSeconds: Math.round(input.durationMs / 1000)
                    }, null, 2);
                """.trimIndent()
            ),
            ScriptExample(
                title = "歌词解析示例",
                script = """
                    const lrc = `
                    [00:01.00]Line one
                    [00:03.50]Line two
                    [00:08.20]Line three
                    `.trim();

                    const lines = lrc
                      .split(/\n+/)
                      .map(line => {
                        const match = line.match(/^\[(\d{2}):(\d{2})\.(\d{2})](.*)$/);
                        if (!match) return null;
                        const minutes = Number(match[1]);
                        const seconds = Number(match[2]);
                        const centiseconds = Number(match[3]);
                        return {
                          timeMs: minutes * 60_000 + seconds * 1000 + centiseconds * 10,
                          text: match[4].trim()
                        };
                      })
                      .filter(Boolean);

                    JSON.stringify(lines, null, 2);
                """.trimIndent()
            ),
            ScriptExample(
                title = "搜索结果排序",
                script = """
                    const query = {
                      title: "One More Light",
                      artist: "Linkin Park"
                    };

                    const results = [
                      { title: "One More Light", artist: "Linkin Park", source: "A" },
                      { title: "One More Night", artist: "Maroon 5", source: "B" },
                      { title: "One More Light Live", artist: "Linkin Park", source: "C" }
                    ];

                    function normalize(value) {
                      return String(value || "")
                        .toLowerCase()
                        .replace(/[()\[\]{}\-_.]/g, " ")
                        .replace(/\s+/g, " ")
                        .trim();
                    }

                    function simpleScore(expected, actual) {
                      expected = normalize(expected);
                      actual = normalize(actual);
                      if (!expected || !actual) return 0;
                      if (expected === actual) return 1;
                      if (actual.includes(expected) || expected.includes(actual)) return 0.85;

                      const a = new Set(expected.split(" "));
                      const b = new Set(actual.split(" "));
                      let hit = 0;
                      for (const item of a) {
                        if (b.has(item)) hit++;
                      }
                      return hit / Math.max(a.size, b.size);
                    }

                    const ranked = results
                      .map(item => ({
                        ...item,
                        score:
                          simpleScore(query.title, item.title) * 0.7 +
                          simpleScore(query.artist, item.artist) * 0.3
                      }))
                      .sort((a, b) => b.score - a.score);

                    JSON.stringify(ranked, null, 2);
                """.trimIndent()
            ),
            ScriptExample(
                title = "插件返回结构模拟",
                script = """
                    function searchSongs(request) {
                      const database = [
                        {
                          title: "Numb",
                          artist: "Linkin Park",
                          album: "Meteora",
                          durationMs: 185586,
                          fields: {
                            genre: "Alternative Rock",
                            date: "2003",
                            bpm: "110"
                          }
                        },
                        {
                          title: "Breaking the Habit",
                          artist: "Linkin Park",
                          album: "Meteora",
                          durationMs: 196906,
                          fields: {
                            genre: "Alternative Rock",
                            date: "2003"
                          }
                        }
                      ];

                      return {
                        success: true,
                        source: "quickjs-demo",
                        query: request.query,
                        page: request.page,
                        results: database.filter(song =>
                          song.title.toLowerCase().includes(request.query.toLowerCase()) ||
                          song.artist.toLowerCase().includes(request.query.toLowerCase())
                        )
                      };
                    }

                    JSON.stringify(searchSongs({ query: "linkin", page: 1 }), null, 2);
                """.trimIndent()
            )
        )
    }

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun runEvalTest(script: String) {
        if (isRunning) return

        val trimmedScript = script.trim()
        if (trimmedScript.isBlank()) {
            evalResult = "⚠️ 脚本不能为空"
            showToast("请输入要执行的脚本")
            return
        }

        scope.launch {
            isRunning = true
            evalResult = "⏳ 执行中..."
            try {
                val result = withContext(Dispatchers.IO) {
                    QuickJsRuntime().use { runtime ->
                        runtime.eval(trimmedScript)
                    }
                }
                evalResult = "✅ 成功:\n$result"
                showToast("eval 测试通过")
            } catch (e: Exception) {
                Log.e("quickjstest", "eval 测试失败:${e.message}", e)
                evalResult = "❌ 失败: ${e.message}"
                showToast("eval 测试失败: ${e.message}")
            } finally {
                isRunning = false
            }
        }
    }

    fun runCallTest() {
        if (isRunning) return
        scope.launch {
            isRunning = true
            try {
                val result = withContext(Dispatchers.IO) {
                    QuickJsRuntime().use { runtime ->
                        runtime.eval(
                            """
                            function searchSongs(request) {
                                return {
                                    success: true,
                                    query: request.query,
                                    results: [
                                        {title: "Test Song 1", artist: "Artist A"},
                                        {title: "Test Song 2", artist: "Artist B"}
                                    ]
                                };
                            }
                            """.trimIndent()
                        )

                        val requestJson = """{"query":"test","page":1}"""
                        runtime.call("searchSongs", requestJson)
                    }
                }
                callResult = "✅ 成功: $result"
                showToast("call 测试通过")
            } catch (e: Exception) {
                Log.e("quickjstest", "call 测试失败:${e.message}", e)
                callResult = "❌ 失败: ${e.message}"
                showToast("call 测试失败: ${e.message}")
            } finally {
                isRunning = false
            }
        }
    }

    fun runTimeoutTest() {
        if (isRunning) return
        scope.launch {
            isRunning = true
            timeoutResult = "⏳ 测试中..."
            try {
                val result = withContext(Dispatchers.IO) {
                    QuickJsRuntime(timeoutMs = 2000).use { runtime ->
                        try {
                            runtime.eval("while(true){}")
                            "❌ 未超时（应该被中断）"
                        } catch (e: Exception) {
                            if (
                                e.message?.contains("timeout", ignoreCase = true) == true ||
                                e.message?.contains("interrupt", ignoreCase = true) == true
                            ) {
                                "✅ 成功超时中断: ${e.message}"
                            } else {
                                "⚠️ 异常但非超时: ${e.message}"
                            }
                        }
                    }
                }
                timeoutResult = result
                showToast("超时测试完成")
            } catch (e: Exception) {
                Log.e("quickjstest", "超时测试失败:${e.message}", e)
                timeoutResult = "❌ 测试异常: ${e.message}"
                showToast("超时测试异常: ${e.message}")
            } finally {
                isRunning = false
            }
        }
    }

    fun runMemoryTest() {
        if (isRunning) return
        scope.launch {
            isRunning = true
            memoryResult = "⏳ 测试中..."
            try {
                val result = withContext(Dispatchers.IO) {
                    QuickJsRuntime(memoryLimitBytes = 4L * 1024L * 1024L).use { runtime ->
                        try {
                            runtime.eval("var arr = new Array(100000000).fill('x');")
                            "❌ 未触发内存限制（应该失败）"
                        } catch (e: Exception) {
                            Log.e("quickjstest", "内存测试失败:${e.message}", e)
                            if (
                                e.message?.contains("memory", ignoreCase = true) == true ||
                                e.message?.contains("allocation", ignoreCase = true) == true ||
                                e.message?.contains("out of memory", ignoreCase = true) == true
                            ) {
                                "✅ 成功触发内存限制: ${e.message}"
                            } else {
                                "⚠️ 异常但非内存错误: ${e.message}"
                            }
                        }
                    }
                }
                memoryResult = result
                showToast("内存测试完成")
            } catch (e: Exception) {
                Log.e("quickjstest", "内存测试失败:${e.message}", e)
                memoryResult = "❌ 测试异常: ${e.message}"
                showToast("内存测试异常: ${e.message}")
            } finally {
                isRunning = false
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.quickjs_test_title),
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .scrollEndHaptic()
                .overScrollVertical(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item("description") {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    BasicComponent(
                        insideMargin = PaddingValues(16.dp),
                        title = stringResource(R.string.quickjs_test_description),
                        summary = stringResource(R.string.quickjs_test_description_summary)
                    )
                }
            }

            item("eval_test") {
                EvalTestCard(
                    title = stringResource(R.string.quickjs_test_eval_title),
                    description = stringResource(R.string.quickjs_test_eval_desc),
                    script = evalScript,
                    onScriptChange = { evalScript = it },
                    examples = evalExamples,
                    result = evalResult,
                    onRun = { runEvalTest(evalScript) },
                    isRunning = isRunning
                )
            }

            item("call_test") {
                TestCard(
                    title = stringResource(R.string.quickjs_test_call_title),
                    description = stringResource(R.string.quickjs_test_call_desc),
                    result = callResult,
                    onRun = ::runCallTest,
                    isRunning = isRunning
                )
            }

            item("timeout_test") {
                TestCard(
                    title = stringResource(R.string.quickjs_test_timeout_title),
                    description = stringResource(R.string.quickjs_test_timeout_desc),
                    result = timeoutResult,
                    onRun = ::runTimeoutTest,
                    isRunning = isRunning
                )
            }

            item("memory_test") {
                TestCard(
                    title = stringResource(R.string.quickjs_test_memory_title),
                    description = stringResource(R.string.quickjs_test_memory_desc),
                    result = memoryResult,
                    onRun = ::runMemoryTest,
                    isRunning = isRunning
                )
            }
        }
    }
}

@Composable
private fun EvalTestCard(
    title: String,
    description: String,
    script: String,
    onScriptChange: (String) -> Unit,
    examples: List<ScriptExample>,
    result: String?,
    onRun: () -> Unit,
    isRunning: Boolean
) {
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "脚本内容",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            BasicTextField(
                value = script,
                onValueChange = onScriptChange,
                enabled = !isRunning,
                textStyle = MiuixTheme.textStyles.body2.copy(
                    color = MiuixTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
                    .border(
                        width = 1.dp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (script.isBlank()) {
                            Text(
                                text = "请输入 JavaScript 脚本，最后一个表达式会作为 eval 返回值",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "示例脚本",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                examples.forEach { example ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            text = example.title,
                            enabled = !isRunning,
                            onClick = { onScriptChange(example.script) },
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    text = stringResource(R.string.quickjs_test_run),
                    onClick = onRun,
                    enabled = !isRunning,
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }

            ResultText(result = result)
        }
    }
}

@Composable
private fun TestCard(
    title: String,
    description: String,
    result: String?,
    onRun: () -> Unit,
    isRunning: Boolean
) {
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    text = stringResource(R.string.quickjs_test_run),
                    onClick = onRun,
                    enabled = !isRunning,
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }

            ResultText(result = result)
        }
    }
}

@Composable
private fun ResultText(result: String?) {
    result?.let {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = it,
            style = MiuixTheme.textStyles.body2,
            color = when {
                it.startsWith("✅") -> MiuixTheme.colorScheme.primary
                it.startsWith("❌") -> MiuixTheme.colorScheme.error
                it.startsWith("⚠️") -> MiuixTheme.colorScheme.tertiaryContainer
                else -> MiuixTheme.colorScheme.onSurfaceVariantActions
            },
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private data class ScriptExample(
    val title: String,
    val script: String
)

private const val DEFAULT_EVAL_SCRIPT = """
const message = "hello quickjs-ng";

({
  original: message,
  upper: message.toUpperCase(),
  length: message.length,
  timestamp: Date.now()
});
"""