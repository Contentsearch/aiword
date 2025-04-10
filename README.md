# AiWord

[![Version](https://img.shields.io/jetbrains/plugin/v/YOUR_PLUGIN_ID.svg?style=flat-square)](https://plugins.jetbrains.com/plugin/YOUR_PLUGIN_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/YOUR_PLUGIN_ID.svg?style=flat-square)](https://github.com/Contentsearch/aiword/releases/download/0.0.1/aiword-0.0.1-SNAPSHOT.zip)

**AiWord** 是一款强大的 IntelliJ IDEA 插件，旨在通过在IDEA中实现背单词的功能，采用快速记忆法的方式来帮助用户快速掌握新单词。

## 主要功能和特性

* **加载本地单词本：** 在设置中配置本地单词本目录，通过加载单词本就可以背单词了
* **单词发音：** 支持单词发音，点击[发音]可以播放单词的发音。
* **随机加载：** 可以翻页也可以随机加载单词

## 安装

你可以通过 IntelliJ IDEA 的插件市场安装 AiEnglishWord 

1.  打开 **Settings/Preferences** (`Ctrl+Alt+S` 或 `Cmd+,`).
2.  导航到 **Plugins**.
3.  在 Marketplace 中搜索 **AiEnglishWord**.
4.  点击 **Install** 并重启 IntelliJ IDEA。

或者，你也可以通过本地安装你构建的 `.zip` 文件：

1.  打开 **Settings/Preferences** (`Ctrl+Alt+S` 或 `Cmd+,`).
2.  导航到 **Plugins**.
3.  点击顶部的齿轮图标 (⚙️)，选择 **Install Plugin from Disk...**.
4.  选择你构建的 `AiWord-x.x.x.zip` 文件并点击 **OK**.
5.  重启 IntelliJ IDEA.

## 如何使用

1.  打开 **Settings/Preferences** (`Ctrl+Alt+S` 或 `Cmd+,`).
2.  在 **Tools** 中找到 **AiWord Memorizer Settings**
3.  配置单词目录，下载单词本，也可以自己添加单词本 要求json格式
4. 格式示例：
``` json
[
    {
        "word": "ability",
        "translations": [
            {
                "translation": "能力，能耐；才能",
                "type": "n"
            }
        ]
    }
]
```
1. 打开右侧工具栏
2. 选择单词本[点击下拉框可以选择单词本]
3. 加载单词本
4. 开始学习



## 屏幕截图

[如果你的 README 中包含屏幕截图，可以在这里添加链接或使用 Markdown 语法嵌入图片。例如：]

![Screenshot 1](/screenshot1.png)
