# ai-launcher

Pixel 風の 3 画面ページャーで構成した、Android 向けのシンプルなホームアプリ（ランチャー）です。

## 特徴

- 3 画面ページャー構成: 左「Discover」/ 中央「Home」/ 右「アプリ一覧」
- 秒表示付きの大きな時計とカレンダーカードを Home に表示
- ホーム画面に App Widget を自由配置（5 列グリッド、幅・高さのリサイズに対応）
- Discover 画面に AI メッセージ枠を表示（差し替え可能）
- 常時ダークモード（端末のテーマ設定に関わらず黒背景・白文字で固定）

## ダウンロード / ビルド

Releases は未整備のため、現状はソースからビルドして利用します。

```bash
./gradlew assembleDebug
```

生成された APK (`app/build/outputs/apk/debug/app-debug.apk`) を端末にインストールしてください。

## インストール後の注意

APK をインストール、または上書きインストールしたあとは、必ずアプリを一度強制停止（force-stop）してから起動してください。ウィジェットのレイアウトや状態が正しく反映されないことがあります。

## 使い方

1. 端末のホームアプリ選択でこのアプリを選ぶ
2. 中央画面（Home）が時計・カレンダー・ウィジェット配置エリア
3. 右スワイプでインストール済みアプリ一覧、左スワイプで Discover 画面

## 必要環境

- Android 12 (API 31) 以上
- ビルドには AGP 9 系 + Kotlin（Kotlin Gradle Plugin は `kotlin-android` ではなく AGP 組み込みの Kotlin サポートを使用）

## ライセンス

MIT License
