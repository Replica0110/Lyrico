@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.about

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lonx.lyrico.App.Companion.OWNER_ID
import com.lonx.lyrico.App.Companion.REPO_NAME
import com.lonx.lyrico.App.Companion.TELEGRAM_GROUP_LINK
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.appearance.UiEngine
import com.lonx.lyrico.data.update.UpdateEffect
import com.lonx.lyrico.ui.components.blur.BlurredTopBar
import com.lonx.lyrico.ui.components.blur.blurSource
import com.lonx.lyrico.ui.components.blur.rememberBarBlurBackdrop
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.pro.ProDeactivationDialog
import com.lonx.lyrico.ui.components.scaffold.PageScaffold
import com.lonx.lyrico.ui.components.scaffold.scaffoldContentPadding
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.screen.ScreenCard
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.OpensourceLicenceDestination
import com.ramcosta.composedestinations.generated.destinations.ProDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import java.io.IOException
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
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
    val scope = rememberCoroutineScope()
    val proActivated by viewModel.proActivated.collectAsStateWithLifecycle()
    var showDisablePro by rememberSaveable { mutableStateOf(false) }
    val version = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"
    val onVersionClick: () -> Unit = {
        scope.launch {
            try {
                when (viewModel.onVersionTap()) {
                    ProVersionTapResult.ACTIVATED -> navigator.navigate(ProDestination())
                    ProVersionTapResult.CONFIRM_DEACTIVATION -> showDisablePro = true
                    ProVersionTapResult.NONE -> Unit
                }
            } catch (_: IOException) {
                Toast.makeText(context, R.string.pro_activation_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
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
    PageScaffold(
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
        val outerMargin = if (salt) SaltTheme.dimens.padding else 12.dp
        val pagePadding = scaffoldContentPadding(paddingValues = paddingValues, bottomExtra = 12.dp)
        val layoutDirection = LocalLayoutDirection.current
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val availableWidth = maxWidth - pagePadding.calculateLeftPadding(layoutDirection) -
                pagePadding.calculateRightPadding(layoutDirection) - outerMargin * 2
            val minCellWidth = 104.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
            val columnCount = ((availableWidth + 8.dp) / (minCellWidth + 8.dp)).toInt().coerceAtLeast(1)
            val contributorRows = remember(contributors, columnCount) { contributors.chunked(columnCount) }
            LazyColumn(
                modifier = (if (salt) Modifier else Modifier
                    .blurSource(topBarBackdrop)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection))
                    .fillMaxHeight(),
                contentPadding = pagePadding,
                overscrollEffect = if (salt) rememberOverscrollEffect() else null,
            ) {
                item {
                    if (salt) {
                        SaltAboutAppInfo(
                            proActivated = proActivated,
                            onPro = { navigator.navigate(ProDestination()) },
                            version = version,
                            onVersionClick = onVersionClick,
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
                                summary = version,
                                onClick = onVersionClick,
                            )
                            if (proActivated) {
                                AboutProEntry(onClick = { navigator.navigate(ProDestination()) })
                            }
                            ArrowPreference(
                                startAction = { PreferenceIcon(LucideIcons.CodeXml, PreferenceIconTone.Tools) },
                                title = stringResource(R.string.about_project_url),
                                summary = stringResource(R.string.about_project_url_sub),
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
                            IllustratedEmptyState(
                                kind = EmptyStateKind.Artists,
                                title = errorText,
                                modifier = Modifier.fillMaxWidth(),
                                actionText = stringResource(R.string.refresh),
                                icon = LucideIcons.RefreshCw,
                                onAction = viewModel::loadContributors,
                            )
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
                        items(
                            items = contributorRows,
                            key = { row -> row.first().id }
                        ) { row ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = outerMargin, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { contributor ->
                                    ContributorGridItem(
                                        name = contributor.login,
                                        avatarUrl = contributor.avatarUrl,
                                        onClick = { viewModel.openBrowser(context, contributor.profileUrl) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(columnCount - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
    ProDeactivationDialog(
        show = showDisablePro,
        onDismissRequest = { showDisablePro = false },
    )
}

@Composable
private fun ContributorGridItem(name: String, avatarUrl: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    val style = if (salt) SaltTheme.textStyles.main.copy(fontSize = 14.sp) else MiuixTheme.textStyles.body2
    val color = if (salt) SaltTheme.colors.text else MiuixTheme.colorScheme.onSurface
    val background = if (salt) SaltTheme.colors.subBackground else MiuixTheme.colorScheme.surfaceContainer
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Text(
            text = name,
            modifier = Modifier.fillMaxWidth(),
            style = style,
            color = color,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SaltAboutAppInfo(
    proActivated: Boolean,
    onPro: () -> Unit,
    version: String,
    onVersionClick: () -> Unit,
    checkUpdateEnabled: Boolean,
    updateSummary: String,
    onProject: () -> Unit,
    onTelegram: () -> Unit,
    onLicences: () -> Unit,
    onCheckUpdateEnabled: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
) {
    val versionLabel = stringResource(R.string.about_app_version)
    ScreenCard(header = stringResource(R.string.about_app_info)) {
        com.moriafly.salt.ui.Item(
            text = versionLabel,
            sub = version,
            iconPainter = rememberVectorPainter(LucideIcons.Info),
            iconColor = PreferenceIconTone.Appearance.color(),
            arrowType = com.moriafly.salt.ui.ItemArrowType.None,
            onClick = onVersionClick,
        )
        if (proActivated) AboutProEntry(onClick = onPro)
        com.moriafly.salt.ui.Item(
            text = stringResource(R.string.about_project_url),
            sub = stringResource(R.string.about_project_url_sub),
            iconPainter = rememberVectorPainter(LucideIcons.CodeXml),
            iconColor = PreferenceIconTone.Tools.color(),
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
