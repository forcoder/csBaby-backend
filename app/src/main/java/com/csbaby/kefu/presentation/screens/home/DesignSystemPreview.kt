package com.csbaby.kefu.presentation.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.csbaby.kefu.presentation.theme.KefuTheme

@Composable
fun DesignSystemPreview() {
    KefuTheme {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text(
                    text = "色彩系统预览",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            item {
                ColorPaletteSection()
            }

            item {
                TypographySection()
            }

            item {
                ComponentPreviewSection()
            }
        }
    }
}

@Composable
private fun ColorPaletteSection() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "颜色调色板",
                style = MaterialTheme.typography.titleLarge
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorSwatch("深海军蓝", MaterialTheme.colorScheme.primary, 40.dp, 40.dp)
                ColorSwatch("海洋青绿", MaterialTheme.colorScheme.secondary, 40.dp, 40.dp)
                ColorSwatch("暖珊瑚橙", MaterialTheme.colorScheme.tertiary, 40.dp, 40.dp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorSwatch("浅灰白", MaterialTheme.colorScheme.background, 40.dp, 40.dp)
                ColorSwatch("深炭黑", MaterialTheme.colorScheme.onBackground, 40.dp, 40.dp)
                ColorSwatch("错误红色", MaterialTheme.colorScheme.error, 40.dp, 40.dp)
            }
        }
    }
}

@Composable
private fun ColorSwatch(name: String, color: androidx.compose.ui.graphics.Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Card(
            colors = CardDefaults.cardColors(containerColor = color),
            modifier = Modifier.size(width, height)
        ) {}
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun TypographySection() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "字体层级预览",
                style = MaterialTheme.typography.titleLarge
            )

            Text("Display Large - 主标题", style = MaterialTheme.typography.displayLarge)
            Text("Display Medium - 大标题", style = MaterialTheme.typography.displayMedium)
            Text("Display Small - 小标题", style = MaterialTheme.typography.displaySmall)

            Text("Headline Large - 一级标题", style = MaterialTheme.typography.headlineLarge)
            Text("Headline Medium - 二级标题", style = MaterialTheme.typography.headlineMedium)
            Text("Headline Small - 三级标题", style = MaterialTheme.typography.headlineSmall)

            Text("Body Large - 正文大号", style = MaterialTheme.typography.bodyLarge)
            Text("Body Medium - 正文", style = MaterialTheme.typography.bodyMedium)
            Text("Body Small - 正文小号", style = MaterialTheme.typography.bodySmall)

            Text("Label Large - 按钮文字", style = MaterialTheme.typography.labelLarge)
            Text("Label Medium - 次要标签", style = MaterialTheme.typography.labelMedium)
            Text("Label Small - 小标签", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComponentPreviewSection() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "组件预览",
                style = MaterialTheme.typography.titleLarge
            )

            Button(
                onClick = { },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("主要操作按钮")
            }

            OutlinedButton(
                onClick = { },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("次要操作按钮")
            }

            TextButton(
                onClick = { },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("文本按钮")
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("示例卡片", style = MaterialTheme.typography.titleMedium)
                    Text("这是一个使用新设计系统的卡片示例。", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DesignSystemPreviewLight() {
    KefuTheme {
        DesignSystemPreview()
    }
}

@Preview(showBackground = true)
@Composable
fun DesignSystemPreviewDark() {
    KefuTheme(darkTheme = true) {
        DesignSystemPreview()
    }
}
