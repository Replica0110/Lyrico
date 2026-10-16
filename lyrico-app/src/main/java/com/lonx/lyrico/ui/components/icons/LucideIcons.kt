package com.lonx.lyrico.ui.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Lucide 1.57.0 SVGs, retaining 24px viewport, 2px round strokes and no fill.
 * Source archive SHA-256: f3c85b385847b4cb56bb027e3960b82b16ffd16978e938ed3a813f701ee51c79
 * ISC / MIT; see assets/licenses/lucide.txt.
 */
internal object LucideIcons {
    val ALargeSmall: ImageVector by lazy {
        lucideIcon("a-large-small",
            "m15 16 2.536-7.328a1.02 1.02 1 0 1 1.928 0L22 16",
            "M15.697 14h5.606",
            "m2 16 4.039-9.69a.5.5 0 0 1 .923 0L11 16",
            "M3.304 13h6.392",
        )
    }

    val Activity: ImageVector by lazy {
        lucideIcon("activity",
            "M22 12h-2.48a2 2 0 0 0-1.93 1.46l-2.35 8.36a.25.25 0 0 1-.48 0L9.24 2.18a.25.25 0 0 0-.48 0l-2.35 8.36A2 2 0 0 1 4.49 12H2",
        )
    }

    val ArrowDownUp: ImageVector by lazy {
        lucideIcon("arrow-down-up",
            "m3 16 4 4 4-4",
            "M7 20V4",
            "m21 8-4-4-4 4",
            "M17 4v16",
        )
    }

    val ArrowLeft: ImageVector by lazy {
        lucideIcon("arrow-left",
            "m12 19-7-7 7-7",
            "M19 12H5",
        )
    }

    val AudioLines: ImageVector by lazy {
        lucideIcon("audio-lines",
            "M2 10v3",
            "M6 6v11",
            "M10 3v18",
            "M14 8v7",
            "M18 5v13",
            "M22 10v3",
        )
    }

    val Blend: ImageVector by lazy {
        lucideIcon("blend",
            "M8 9a7 7 0 1 0 14 0a7 7 0 1 0 -14 0",
            "M2 15a7 7 0 1 0 14 0a7 7 0 1 0 -14 0",
        )
    }

    val Calendar: ImageVector by lazy {
        lucideIcon("calendar",
            "M8 2v3",
            "M16 2v3",
            "M5.0 3.0H19.0a2.0 2.0 0 0 1 2.0 2.0V19.0a2.0 2.0 0 0 1 -2.0 2.0H5.0a2.0 2.0 0 0 1 -2.0 -2.0V5.0a2.0 2.0 0 0 1 2.0 -2.0Z",
            "M3 9h18",
        )
    }

    val Captions: ImageVector by lazy {
        lucideIcon("captions",
            "M5.0 5.0H19.0a2.0 2.0 0 0 1 2.0 2.0V17.0a2.0 2.0 0 0 1 -2.0 2.0H5.0a2.0 2.0 0 0 1 -2.0 -2.0V7.0a2.0 2.0 0 0 1 2.0 -2.0Z",
            "M7 15h4M15 15h2M7 11h2M13 11h4",
        )
    }

    val Check: ImageVector by lazy {
        lucideIcon("check",
            "M20 6 9 17l-5-5",
        )
    }

    val ClipboardList: ImageVector by lazy {
        lucideIcon("clipboard-list",
            "M9.0 2.0H15.0a1.0 1.0 0 0 1 1.0 1.0V5.0a1.0 1.0 0 0 1 -1.0 1.0H9.0a1.0 1.0 0 0 1 -1.0 -1.0V3.0a1.0 1.0 0 0 1 1.0 -1.0Z",
            "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2",
            "M12 11h4",
            "M12 16h4",
            "M8 11h.01",
            "M8 16h.01",
        )
    }

    val CloudDownload: ImageVector by lazy {
        lucideIcon("cloud-download",
            "M12 13v8l-4-4",
            "m12 21 4-4",
            "M4.393 15.269A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.436 8.284",
        )
    }

    val CodeXml: ImageVector by lazy {
        lucideIcon("code-xml",
            "m18 16 4-4-4-4",
            "m6 8-4 4 4 4",
            "m14.5 4-5 16",
        )
    }

    val Copy: ImageVector by lazy {
        lucideIcon("copy",
            "M10.0 8.0H20.0a2.0 2.0 0 0 1 2.0 2.0V20.0a2.0 2.0 0 0 1 -2.0 2.0H10.0a2.0 2.0 0 0 1 -2.0 -2.0V10.0a2.0 2.0 0 0 1 2.0 -2.0Z",
            "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2",
        )
    }

    val Copyright: ImageVector by lazy {
        lucideIcon("copyright",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
            "M14.83 14.83a4 4 0 1 1 0-5.66",
        )
    }

    val Crop: ImageVector by lazy {
        lucideIcon("crop",
            "M6 2v14a2 2 0 0 0 2 2h14",
            "M18 22V8a2 2 0 0 0-2-2H2",
        )
    }

    val Database: ImageVector by lazy {
        lucideIcon("database",
            "M3 5a9 3 0 1 0 18 0a9 3 0 1 0 -18 0",
            "M3 5V19A9 3 0 0 0 21 19V5",
            "M3 12A9 3 0 0 0 21 12",
        )
    }

    val Disc: ImageVector by lazy {
        lucideIcon("disc",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
            "M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        )
    }

    val Download: ImageVector by lazy {
        lucideIcon("download",
            "M12 15V3",
            "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
            "m7 10 5 5 5-5",
        )
    }

    val EllipsisVertical: ImageVector by lazy {
        lucideIcon("ellipsis-vertical",
            "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
            "M11 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
            "M11 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
        )
    }

    val Eraser: ImageVector by lazy {
        lucideIcon("eraser",
            "M21 21H8a2 2 0 0 1-1.42-.587l-3.994-3.999a2 2 0 0 1 0-2.828l10-10a2 2 0 0 1 2.829 0l5.999 6a2 2 0 0 1 0 2.828L12.834 21",
            "m5.082 11.09 8.828 8.828",
        )
    }

    val Eye: ImageVector by lazy {
        lucideIcon("eye",
            "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0",
            "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
        )
    }

    val EyeOff: ImageVector by lazy {
        lucideIcon("eye-off",
            "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49",
            "M14.084 14.158a3 3 0 0 1-4.242-4.242",
            "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143",
            "m2 2 20 20",
        )
    }

    val FileDown: ImageVector by lazy {
        lucideIcon("file-down",
            "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M12 18v-6",
            "m9 15 3 3 3-3",
        )
    }

    val FileMusic: ImageVector by lazy {
        lucideIcon("file-music",
            "M11.65 22H18a2 2 0 0 0 2-2V8a2.4 2.4 0 0 0-.706-1.706l-3.588-3.588A2.4 2.4 0 0 0 14 2H6a2 2 0 0 0-2 2v10.35",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M8 20v-7l3 1.474",
            "M4 20a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        )
    }

    val FileUp: ImageVector by lazy {
        lucideIcon("file-up",
            "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M12 12v6",
            "m15 15-3-3-3 3",
        )
    }

    val Folder: ImageVector by lazy {
        lucideIcon("folder",
            "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
        )
    }

    val FolderPlus: ImageVector by lazy {
        lucideIcon("folder-plus",
            "M12 10v6",
            "M9 13h6",
            "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
        )
    }

    val Image: ImageVector by lazy {
        lucideIcon("image",
            "M5.0 3.0H19.0a2.0 2.0 0 0 1 2.0 2.0V19.0a2.0 2.0 0 0 1 -2.0 2.0H5.0a2.0 2.0 0 0 1 -2.0 -2.0V5.0a2.0 2.0 0 0 1 2.0 -2.0Z",
            "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
            "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
        )
    }

    val Info: ImageVector by lazy {
        lucideIcon("info",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
            "M12 16v-4",
            "M12 8h.01",
        )
    }

    val Languages: ImageVector by lazy {
        lucideIcon("languages",
            "m5 8 6 6",
            "m4 14 6-6 2-3",
            "M2 5h12",
            "M7 2h1",
            "m22 22-5-10-5 10",
            "M14 18h6",
        )
    }

    val Layers: ImageVector by lazy {
        lucideIcon("layers",
            "M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z",
            "M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12",
            "M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17",
        )
    }

    val LayoutGrid: ImageVector by lazy {
        lucideIcon("layout-grid",
            "M4.0 3.0H9.0a1.0 1.0 0 0 1 1.0 1.0V9.0a1.0 1.0 0 0 1 -1.0 1.0H4.0a1.0 1.0 0 0 1 -1.0 -1.0V4.0a1.0 1.0 0 0 1 1.0 -1.0Z",
            "M15.0 3.0H20.0a1.0 1.0 0 0 1 1.0 1.0V9.0a1.0 1.0 0 0 1 -1.0 1.0H15.0a1.0 1.0 0 0 1 -1.0 -1.0V4.0a1.0 1.0 0 0 1 1.0 -1.0Z",
            "M15.0 14.0H20.0a1.0 1.0 0 0 1 1.0 1.0V20.0a1.0 1.0 0 0 1 -1.0 1.0H15.0a1.0 1.0 0 0 1 -1.0 -1.0V15.0a1.0 1.0 0 0 1 1.0 -1.0Z",
            "M4.0 14.0H9.0a1.0 1.0 0 0 1 1.0 1.0V20.0a1.0 1.0 0 0 1 -1.0 1.0H4.0a1.0 1.0 0 0 1 -1.0 -1.0V15.0a1.0 1.0 0 0 1 1.0 -1.0Z",
        )
    }

    val List: ImageVector by lazy {
        lucideIcon("list",
            "M3 5h.01",
            "M3 12h.01",
            "M3 19h.01",
            "M8 5h13",
            "M8 12h13",
            "M8 19h13",
        )
    }

    val ListChecks: ImageVector by lazy {
        lucideIcon("list-checks",
            "M13 5h8",
            "M13 12h8",
            "M13 19h8",
            "m3 17 2 2 4-4",
            "m3 7 2 2 4-4",
        )
    }

    val ListFilter: ImageVector by lazy {
        lucideIcon("list-filter",
            "M2 5h20",
            "M6 12h12",
            "M9 19h6",
        )
    }

    val RemoveLines: ImageVector by lazy {
        lucideIcon("list-minus",
            "M16 5H3",
            "M11 12H3",
            "M16 19H3",
            "M21 12h-6",
        )
    }

    val ListMusic: ImageVector by lazy {
        lucideIcon("list-music",
            "M16 5H3",
            "M11 12H3",
            "M11 19H3",
            "M21 16V5",
            "M15 16a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
        )
    }

    val FilterLyrics: ImageVector by lazy {
        lucideIcon("list-filter",
            "M2 5h20",
            "M6 12h12",
            "M9 19h6",
        )
    }

    val Logs: ImageVector by lazy {
        lucideIcon("logs",
            "M3 5h1",
            "M3 12h1",
            "M3 19h1",
            "M8 5h1",
            "M8 12h1",
            "M8 19h1",
            "M13 5h8",
            "M13 12h8",
            "M13 19h8",
        )
    }

    val MessageSquare: ImageVector by lazy {
        lucideIcon("message-square",
            "M22 17a2 2 0 0 1-2 2H6.828a2 2 0 0 0-1.414.586l-2.202 2.202A.71.71 0 0 1 2 21.286V5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2z",
        )
    }

    val Moon: ImageVector by lazy {
        lucideIcon("moon",
            "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401",
        )
    }

    val Music: ImageVector by lazy {
        lucideIcon("music",
            "M9 18V5l12-2v13",
            "M3 18a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M15 16a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
        )
    }

    val NotebookPen: ImageVector by lazy {
        lucideIcon("notebook-pen",
            "M13.4 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7.4",
            "M2 6h4",
            "M2 10h4",
            "M2 14h4",
            "M2 18h4",
            "M21.378 5.626a1 1 0 1 0-3.004-3.004l-5.01 5.012a2 2 0 0 0-.506.854l-.837 2.87a.5.5 0 0 0 .62.62l2.87-.837a2 2 0 0 0 .854-.506z",
        )
    }

    val Palette: ImageVector by lazy {
        lucideIcon("palette",
            "M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z",
            "M13 6.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0",
            "M17 10.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0",
            "M6 12.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0",
            "M8 7.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0",
        )
    }

    val PanelBottom: ImageVector by lazy {
        lucideIcon("panel-bottom",
            "M5.0 3.0H19.0a2.0 2.0 0 0 1 2.0 2.0V19.0a2.0 2.0 0 0 1 -2.0 2.0H5.0a2.0 2.0 0 0 1 -2.0 -2.0V5.0a2.0 2.0 0 0 1 2.0 -2.0Z",
            "M3 15h18",
        )
    }

    val PanelsTopLeft: ImageVector by lazy {
        lucideIcon("panels-top-left",
            "M5.0 3.0H19.0a2.0 2.0 0 0 1 2.0 2.0V19.0a2.0 2.0 0 0 1 -2.0 2.0H5.0a2.0 2.0 0 0 1 -2.0 -2.0V5.0a2.0 2.0 0 0 1 2.0 -2.0Z",
            "M3 9h18",
            "M9 21V9",
        )
    }

    val Pencil: ImageVector by lazy {
        lucideIcon("pencil",
            "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
            "m15 5 4 4",
        )
    }

    val Plug: ImageVector by lazy {
        lucideIcon("plug",
            "M12 22v-5",
            "M15 8V2",
            "M17 8a1 1 0 0 1 1 1v4a4 4 0 0 1-4 4h-4a4 4 0 0 1-4-4V9a1 1 0 0 1 1-1z",
            "M9 8V2",
        )
    }

    val Plus: ImageVector by lazy {
        lucideIcon("plus",
            "M5 12h14",
            "M12 5v14",
        )
    }

    val RefreshCw: ImageVector by lazy {
        lucideIcon("refresh-cw",
            "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
            "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
            "M8 16H3v5",
        )
    }

    val RotateCcw: ImageVector by lazy {
        lucideIcon("rotate-ccw",
            "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
            "M3 3v5h5",
        )
    }

    val Scale: ImageVector by lazy {
        lucideIcon("scale",
            "M12 3v18",
            "m19 8 3 8a5 5 0 0 1-6 0zV7",
            "M3 7h1a17 17 0 0 0 8-2 17 17 0 0 0 8 2h1",
            "m5 8 3 8a5 5 0 0 1-6 0zV7",
            "M7 21h10",
        )
    }

    val Send: ImageVector by lazy {
        lucideIcon("send",
            "M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z",
            "m21.854 2.147-10.94 10.939",
        )
    }

    // Lucide bolt: a hexagonal nut used for the app's settings actions.
    val Settings: ImageVector by lazy {
        lucideIcon("bolt",
            "M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z",
            "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
        )
    }

    val Share2: ImageVector by lazy {
        lucideIcon("share-2",
            "M15 5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M3 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M15 19a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M8.59 13.51L15.42 17.49",
            "M15.41 6.51L8.59 10.49",
        )
    }

    val SlidersHorizontal: ImageVector by lazy {
        lucideIcon("sliders-horizontal",
            "M10 5H3",
            "M12 19H3",
            "M14 3v4",
            "M16 17v4",
            "M21 12h-9",
            "M21 19h-5",
            "M21 5h-7",
            "M8 10v4",
            "M8 12H3",
        )
    }

    val Split: ImageVector by lazy {
        lucideIcon("split",
            "M16 3h5v5",
            "M8 3H3v5",
            "M12 22v-8.3a4 4 0 0 0-1.172-2.872L3 3",
            "m15 9 6-6",
        )
    }

    val Star: ImageVector by lazy {
        lucideIcon("star",
            "M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z",
        )
    }

    val Tags: ImageVector by lazy {
        lucideIcon("tags",
            "M13.172 2a2 2 0 0 1 1.414.586l6.71 6.71a2.4 2.4 0 0 1 0 3.408l-4.592 4.592a2.4 2.4 0 0 1-3.408 0l-6.71-6.71A2 2 0 0 1 6 9.172V3a1 1 0 0 1 1-1z",
            "M2 7v6.172a2 2 0 0 0 .586 1.414l6.71 6.71a2.4 2.4 0 0 0 3.191.193",
            "M10 6.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0",
        )
    }

    val Terminal: ImageVector by lazy {
        lucideIcon("terminal",
            "M12 19h8",
            "m4 17 6-6-6-6",
        )
    }

    val TextSearch: ImageVector by lazy {
        lucideIcon("text-search",
            "M21 5H3",
            "M10 12H3",
            "M10 19H3",
            "M14 15a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "m21 19-1.9-1.9",
        )
    }

    val Trash: ImageVector by lazy {
        lucideIcon("trash",
            "M10 11v6",
            "M14 11v6",
            "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
            "M3 6h18",
            "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
        )
    }

    val Type: ImageVector by lazy {
        lucideIcon("type",
            "M12 4v16",
            "M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2",
            "M9 20h6",
        )
    }

    val UserPlus: ImageVector by lazy {
        lucideIcon("user-plus",
            "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
            "M5 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
            "M19 8L19 14",
            "M22 11L16 11",
        )
    }

    val Users: ImageVector by lazy {
        lucideIcon("users",
            "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
            "M16 3.128a4 4 0 0 1 0 7.744",
            "M22 21v-2a4 4 0 0 0-3-3.87",
            "M5 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
        )
    }

    val Volume2: ImageVector by lazy {
        lucideIcon("volume-2",
            "M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z",
            "M16 9a5 5 0 0 1 0 6",
            "M19.364 18.364a9 9 0 0 0 0-12.728",
        )
    }

    val Play: ImageVector by lazy {
        lucideIcon("play",
            "M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z",
        )
    }

    val Search: ImageVector by lazy {
        lucideIcon("search",
            "m21 21-4.34-4.34",
            "M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0",
        )
    }

    val ImageAdd: ImageVector by lazy {
        lucideIcon("image-plus",
            "M16 5h6",
            "M19 2v6",
            "M21 11.5V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7.5",
            "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
            "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        )
    }

    val UserSwitch: ImageVector by lazy {
        lucideIcon("users-round",
            "M18 21a8 8 0 0 0-16 0",
            "M5 8a5 5 0 1 0 10 0a5 5 0 1 0 -10 0",
            "M22 20c0-3.37-2-6.5-4-8a5 5 0 0 0-.45-8.3",
        )
    }

    val Timing: ImageVector by lazy {
        lucideIcon("timer",
            "M10 2L14 2",
            "M12 14L15 11",
            "M4 14a8 8 0 1 0 16 0a8 8 0 1 0 -16 0",
        )
    }

    val Convert: ImageVector by lazy {
        lucideIcon("replace-all",
            "M14 14a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1",
            "M14 4a1 1 0 0 1 1-1",
            "M15 10a1 1 0 0 1-1-1",
            "M19 14a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1",
            "M21 4a1 1 0 0 0-1-1",
            "M21 9a1 1 0 0 1-1 1",
            "m3 7 3 3 3-3",
            "M6 10V5a2 2 0 0 1 2-2h2",
            "M4.0 14.0H9.0a1.0 1.0 0 0 1 1.0 1.0V20.0a1.0 1.0 0 0 1 -1.0 1.0H4.0a1.0 1.0 0 0 1 -1.0 -1.0V15.0a1.0 1.0 0 0 1 1.0 -1.0Z",
        )
    }

    val Sort: ImageVector get() = ListFilter

    val ImageSearch: ImageVector by lazy {
        lucideIcon("image",
            "M5.0 3.0H19.0a2.0 2.0 0 0 1 2.0 2.0V19.0a2.0 2.0 0 0 1 -2.0 2.0H5.0a2.0 2.0 0 0 1 -2.0 -2.0V5.0a2.0 2.0 0 0 1 2.0 -2.0Z",
            "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
            "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
        )
    }

    val ImageLibrary: ImageVector by lazy {
        lucideIcon("images",
            "m22 11-1.296-1.296a2.4 2.4 0 0 0-3.408 0L11 16",
            "M4 8a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2",
            "M12 7a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
            "M10.0 2.0H20.0a2.0 2.0 0 0 1 2.0 2.0V14.0a2.0 2.0 0 0 1 -2.0 2.0H10.0a2.0 2.0 0 0 1 -2.0 -2.0V4.0a2.0 2.0 0 0 1 2.0 -2.0Z",
        )
    }

    val Undo: ImageVector by lazy {
        lucideIcon("undo",
            "M3 7v6h6",
            "M21 17a9 9 0 0 0-9-9 9 9 0 0 0-6 2.3L3 13",
        )
    }

    val OpenInNew: ImageVector by lazy {
        lucideIcon("external-link",
            "M15 3h6v6",
            "M10 14 21 3",
            "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6",
        )
    }

    val Parallel: ImageVector by lazy {
        lucideIcon("columns-2",
            "M5.0 3.0H19.0a2.0 2.0 0 0 1 2.0 2.0V19.0a2.0 2.0 0 0 1 -2.0 2.0H5.0a2.0 2.0 0 0 1 -2.0 -2.0V5.0a2.0 2.0 0 0 1 2.0 -2.0Z",
            "M12 3v18",
        )
    }

    val Close: ImageVector by lazy {
        lucideIcon("x",
            "M18 6 6 18",
            "m6 6 12 12",
        )
    }

    val ArrowRight: ImageVector by lazy {
        lucideIcon("arrow-right",
            "M5 12h14",
            "m12 5 7 7-7 7",
        )
    }

    val Link: ImageVector by lazy {
        lucideIcon("link",
            "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
            "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71",
        )
    }

}

private fun lucideIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { data ->
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
