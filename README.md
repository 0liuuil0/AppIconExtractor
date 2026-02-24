# 应用图标提取器 (App Icon Extractor)

一款 Android 应用，可提取手机内所有已安装应用的图标并保存到本地。

## 功能特点

- 🚀 **一键提取** - 快速提取所有已安装应用的图标
- 📱 **全面兼容** - 亲测`Android 16`环境下可用
- 💾 **自动保存** - 图标自动保存到 `./Pictures/AppIcons` 目录
- 🎨 **高清图标** - 支持自适应图标`Adaptive Icon`，保持原始分辨率
- 📊 **实时进度** - 显示提取进度和状态

## 项目结构

```
AppIconExtractor/
├── app/
│   ├── src/main/
│   │   ├── java/com/appiconextractor/
│   │   │   └── MainActivity.kt          # 主Activity
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml    # 主界面布局
│   │   │   │   └── item_app.xml         # 列表项布局
│   │   │   ├── values/
│   │   │   │   ├── strings.xml          # 字符串资源
│   │   │   │   ├── colors.xml           # 颜色资源
│   │   │   │   └── themes.xml           # 主题样式
│   │   │   ├── xml/
│   │   │   │   └── file_paths.xml       # FileProvider配置
│   │   │   └── mipmap-*/                # 应用图标(可自行添加)
│   │   └── AndroidManifest.xml          # 清单文件
│   ├── build.gradle                     # 应用级构建配置
│   └── proguard-rules.pro               # 混淆规则
├── gradle/wrapper/
│   └── gradle-wrapper.properties        # Gradle版本配置
├── build.gradle                         # 项目级构建配置
├── settings.gradle                      # 项目设置
└── gradle.properties                    # Gradle属性
```

## 编译方法

### 方法一：使用 Android Studio (推荐)

1. 安装 [Android Studio](https://developer.android.com/studio) (最新版本)
2. 打开 Android Studio，选择 `Open an Existing Project`
3. 选择 `AppIconExtractor` 文件夹
4. 等待 Gradle 同步完成
5. 点击 `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
6. 编译完成后，APK 位于：`app/build/outputs/apk/debug/app-debug.apk`

### 方法二：使用命令行

```bash
# 进入项目目录
cd AppIconExtractor

# 编译 Debug APK
./gradlew assembleDebug

# 或编译 Release APK
./gradlew assembleRelease
```

### 方法三：使用 Gradle (无 gradlew)

```bash
# 确保已安装 Gradle 8.2+
cd AppIconExtractor
gradle assembleDebug
```

## 安装与使用

1. 将 APK 安装到手机：
   ```bash
   adb install app-debug.apk
   ```

2. 打开应用，首次运行需要授权存储权限

3. 点击 **"一键提取全部图标"** 按钮

4. 图标保存路径：`/sdcard/Pictures/AppIcons_时间戳/`

## 权限说明

| 权限 | 用途 |
|------|------|
| QUERY_ALL_PACKAGES | 获取已安装应用列表 (Android 11+) |
| WRITE_EXTERNAL_STORAGE | 保存图标文件 (Android 9及以下) |
| READ_MEDIA_IMAGES | 保存图标文件 (Android 13+) |

## 系统要求

- Android 7.0 (API 24) 及以上
- 推荐使用 Android 10+ 以获得最佳体验

## 自定义应用图标

如需自定义应用图标，请在以下目录放置对应尺寸的图标文件：

```
res/
├── mipmap-mdpi/ic_launcher.png      # 48x48
├── mipmap-hdpi/ic_launcher.png      # 72x72
├── mipmap-xhdpi/ic_launcher.png     # 96x96
├── mipmap-xxhdpi/ic_launcher.png    # 144x144
└── mipmap-xxxhdpi/ic_launcher.png   # 192x192
```

## 技术栈

- Kotlin
- AndroidX
- Material Design Components
- Kotlin Coroutines
- ViewBinding

## License

MIT License
