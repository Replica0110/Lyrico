@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.AsyncImage
import com.lonx.lyrico.App.Companion.OWNER_ID
import com.lonx.lyrico.App.Companion.REPO_NAME
import com.lonx.lyrico.App.Companion.TELEGRAM_GROUP_LINK
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.ui.components.library.LibraryDetailPage
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.screen.ScreenCard
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.ui.components.blur.BlurredTopBar
import com.lonx.lyrico.ui.components.blur.blurSource
import com.lonx.lyrico.ui.components.blur.rememberBarBlurBackdrop
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.lonx.lyrico.utils.UpdateEffect
import com.lonx.lyrico.viewmodel.AboutViewModel
import com.lonx.lyrico.viewmodel.UiError
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.OpensourceLicenceDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
@Destination<RootGraph>(route = "about")
fun AboutScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: AboutViewModel = koinViewModel()
    val checkUpdateEnabled by viewModel.checkUpdateEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val updateEffect by viewModel.updateEffect.collectAsStateWithLifecycle(
        initialValue = UpdateEffect(R.string.about_check_update_default)
    )
    val contributors by viewModel.contributors.collectAsStateWithLifecycle()
    val loading by viewModel.loadingContributors.collectAsStateWithLifecycle()
    val error by viewModel.contributorsError.collectAsStateWithLifecycle()

    val errorText = when (val e = error) {
        null -> null
        UiError.LoadFailed -> stringResource(R.string.load_failed)
        is UiError.Message -> e.text
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val topBarBackdrop = rememberBarBlurBackdrop()
    val salt = LocalUiEngine.current == UiEngine.SALT
    LibraryDetailPage(
        title = stringResource(R.string.about_title),
        onBack = { navigator.popBackStack() },
        miuixTopBar = {
            BlurredTopBar(backdrop = topBarBackdrop) {
                SmallTopAppBar(
                    color = Color.Transparent,
                    defaultWindowInsetsPadding = false,
                    title = stringResource(R.string.about_title),
                    navigationIcon = {
                        IconButton(
                            onClick = { navigator.popBackStack() }
                        ) {
                            Icon(
                                LucideIcons.ArrowLeft,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    scrollBehavior = topAppBarScrollBehavior
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = (if (salt) Modifier else Modifier
                .blurSource(topBarBackdrop)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection))
                .fillMaxHeight(),
            contentPadding = scaffoldContentPadding(
                paddingValues = paddingValues,
                bottomExtra = 12.dp
            ),
            overscrollEffect = if (salt) rememberOverscrollEffect() else null,
        ) {
            item {
                if (salt) {
                    SaltAboutAppInfo(
                        checkUpdateEnabled = checkUpdateEnabled,
                        updateSummary = stringResource(updateEffect.messageRes, *updateEffect.formatArgs.toTypedArray()),
                        onProject = { viewModel.openBrowser(context, "https://github.com/$OWNER_ID/$REPO_NAME") },
                        onTelegram = { viewModel.openBrowser(context, TELEGRAM_GROUP_LINK) },
                        onLicences = { navigator.navigate(OpensourceLicenceDestination()) },
                        onCheckUpdateEnabled = viewModel::setCheckUpdateEnabled,
                        onCheckUpdate = viewModel::checkUpdate
                    )
                } else {
                    SmallTitle(text = stringResource(R.string.about_app_info))
                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        BasicComponent(
                            startAction = { PreferenceIcon(LucideIcons.Info, PreferenceIconTone.Appearance) },
                            title = stringResource(R.string.about_app_version),
                            summary = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"
                        )
                        ArrowPreference(
                            startAction = { PreferenceIcon(LucideIcons.CodeXml, PreferenceIconTone.Tools) },
                            title = stringResource(R.string.about_project_url),
                            summary = stringResource(R.string.about_project_url_sub),
                            endActions = {
                                Text(
                                    text = "GitHub",
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                            },
                            onClick = {
                                viewModel.openBrowser(
                                    context,
                                    "https://github.com/$OWNER_ID/$REPO_NAME"
                                )
                            }
                        )
                        ArrowPreference(
                            startAction = { PreferenceIcon(LucideIcons.Send, PreferenceIconTone.Library) },
                            title = "Telegram",
                            summary = TELEGRAM_GROUP_LINK,
                            endActions = {
                                Text(
                                    text = "Telegram",
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                            },
                            onClick = {
                                viewModel.openBrowser(context, TELEGRAM_GROUP_LINK)
                            }
                        )
                        ArrowPreference(
                            startAction = { PreferenceIcon(LucideIcons.Scale, PreferenceIconTone.Lyrics) },
                            title = stringResource(R.string.title_opensource_licence),
                            onClick = {
                                navigator.navigate(OpensourceLicenceDestination())
                            }
                        )
                        SwitchPreference(
                            startAction = { PreferenceIcon(LucideIcons.CloudDownload, PreferenceIconTone.Pink) },
                            title = stringResource(R.string.about_auto_check_update),
                            summary = stringResource(R.string.about_auto_check_update_sub),
                            checked = checkUpdateEnabled,
                            onCheckedChange = viewModel::setCheckUpdateEnabled
                        )
                        ArrowPreference(
                            startAction = { PreferenceIcon(LucideIcons.Download, PreferenceIconTone.Teal) },
                            title = stringResource(R.string.about_check_update),
                            summary = stringResource(
                                updateEffect.messageRes,
                                *updateEffect.formatArgs.toTypedArray()
                            ),
                            onClick = viewModel::checkUpdate
                        )
                    }
                }
            }

            item {
                if (salt) com.moriafly.salt.ui.ItemOuterTitle(stringResource(R.string.about_contributors))
                else SmallTitle(text = stringResource(R.string.about_contributors))
            }

            when {
                loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (salt) androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = SaltTheme.colors.highlight,
                                trackColor = SaltTheme.colors.stroke
                            ) else CircularProgressIndicator(size = 32.dp)
                        }
                    }
                }

                errorText != null -> {
                    item {
                        if (salt) {
                            IllustratedEmptyState(
                                kind = EmptyStateKind.Artists,
                                title = errorText,
                                compact = true,
                                modifier = Modifier.fillMaxWidth(),
                                actionText = stringResource(R.string.refresh),
                                onAction = viewModel::loadContributors
                            )
                        } else {
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .fillMaxWidth()
                            ) {
                                BasicComponent(
                                    onClick = {
                                        viewModel.loadContributors()
                                    }
                                ) {
                                    Text(
                                        text = errorText,
                                        modifier = Modifier.padding(12.dp),
                                        color = MiuixTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                contributors.isEmpty() -> {
                    item {
                        IllustratedEmptyState(
                            kind = EmptyStateKind.Artists,
                            title = stringResource(R.string.about_no_contributors),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                else -> {
                    itemsIndexed(
                        items = contributors,
                        key = { _, contributor -> contributor.id }
                    ) { index, contributor ->
                        if (salt) {
                            ScreenCard {
                                com.moriafly.salt.ui.Item(
                                    text = contributor.login,
                                    sub = stringResource(R.string.about_contribution_count, contributor.contributions),
                                    iconPainter = rememberAsyncImagePainter(contributor.avatar_url),
                                    iconColor = null,
                                    tag = "GitHub",
                                    onClick = { viewModel.openBrowser(context, contributor.html_url) }
                                )
                            }
                        } else {
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .padding(top = if (index == 0) 0.dp else 12.dp)
                            ) {
                                ArrowPreference(
                                    title = contributor.login,
                                    summary = stringResource(
                                        R.string.about_contribution_count,
                                        contributor.contributions
                                    ),
                                    startAction = {
                                        AsyncImage(
                                            model = contributor.avatar_url,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .padding(end = 16.dp)
                                                .size(36.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    },
                                    endActions = {
                                        Text(
                                            text = "GitHub",
                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    },
                                    onClick = {
                                        viewModel.openBrowser(context, contributor.html_url)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaltAboutAppInfo(
    checkUpdateEnabled: Boolean,
    updateSummary: String,
    onProject: () -> Unit,
    onTelegram: () -> Unit,
    onLicences: () -> Unit,
    onCheckUpdateEnabled: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val versionLabel = stringResource(R.string.about_app_version)
    val version = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"
    ScreenCard(header = stringResource(R.string.about_app_info)) {
        com.moriafly.salt.ui.Item(
            text = versionLabel,
            sub = version,
            iconPainter = rememberVectorPainter(LucideIcons.Info),
            iconColor = PreferenceIconTone.Appearance.color(),
            arrowType = com.moriafly.salt.ui.ItemArrowType.None,
            onClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(versionLabel, version)))
                    Toast.makeText(context, R.string.msg_copied_to_clipboard, Toast.LENGTH_SHORT).show()
                }
            },
        )
        com.moriafly.salt.ui.Item(
            text = stringResource(R.string.about_project_url),
            sub = stringResource(R.string.about_project_url_sub),
            iconPainter = rememberVectorPainter(LucideIcons.CodeXml),
            iconColor = PreferenceIconTone.Tools.color(),
            tag = "GitHub",
            onClick = onProject
        )
        com.moriafly.salt.ui.Item(
            text = "Telegram",
            sub = TELEGRAM_GROUP_LINK,
            iconPainter = rememberVectorPainter(LucideIcons.Send),
            iconColor = PreferenceIconTone.Library.color(),
            onClick = onTelegram
        )
        com.moriafly.salt.ui.Item(
            text = stringResource(R.string.title_opensource_licence),
            iconPainter = rememberVectorPainter(LucideIcons.Scale),
            iconColor = PreferenceIconTone.Lyrics.color(),
            onClick = onLicences
        )
        com.moriafly.salt.ui.ItemSwitcher(
            text = stringResource(R.string.about_auto_check_update),
            sub = stringResource(R.string.about_auto_check_update_sub),
            iconPainter = rememberVectorPainter(LucideIcons.CloudDownload),
            iconColor = PreferenceIconTone.Pink.color(),
            state = checkUpdateEnabled,
            onChange = onCheckUpdateEnabled
        )
        com.moriafly.salt.ui.Item(
            text = stringResource(R.string.about_check_update),
            sub = updateSummary,
            iconPainter = rememberVectorPainter(LucideIcons.Download),
            iconColor = PreferenceIconTone.Teal.color(),
            onClick = onCheckUpdate
        )
    }
}
