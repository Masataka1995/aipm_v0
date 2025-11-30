# Gitリポジトリのセットアップ手順

## 1. Gitのインストール確認

Gitがインストールされているか確認してください。
- コマンドプロンプトまたはPowerShellで `git --version` を実行
- インストールされていない場合は https://git-scm.com/download/win からインストール

## 2. Gitリポジトリの初期化

プロジェクトディレクトリで以下のコマンドを実行:

```batch
# Gitリポジトリを初期化
git init

# 現在のブランチ名をmainに設定（GitHubのデフォルト）
git branch -M main

# すべてのファイルをステージング
git add .

# 初回コミット
git commit -m "Initial commit: Jicoo自動予約BOT

- 予約監視機能
- Web UI
- スリープモード対応
- レッスン時間累積表示機能"
```

## 3. GitHubでリポジトリを作成

1. https://github.com/new にアクセス
2. リポジトリ名を入力（例: `jicoo-reservation-bot`）
3. 説明を入力（例: "Jicoo自動予約BOT - 英会話レッスンの自動予約システム"）
4. Public または Private を選択
5. 「Initialize this repository with a README」はチェックしない（既にファイルがあるため）
6. 「Create repository」をクリック

## 4. リモートリポジトリを追加してプッシュ

GitHubでリポジトリを作成した後、表示されるURLを使用:

```batch
# リモートリポジトリを追加（URLは実際のリポジトリURLに置き換えてください）
git remote add origin https://github.com/YOUR_USERNAME/jicoo-reservation-bot.git

# リモートリポジトリのURLを確認
git remote -v

# プッシュ
git push -u origin main
```

## 5. 今後の更新方法

変更をコミットしてプッシュする場合:

```batch
# 変更をステージング
git add .

# コミット
git commit -m "変更内容の説明"

# プッシュ
git push
```

## 注意事項

- `.gitignore`ファイルで以下のファイル/ディレクトリは除外されています:
  - `target/` (ビルド成果物)
  - `logs/` (ログファイル)
  - `node_modules/` (Node.js依存関係)
  - `*.log` (ログファイル)
  - `*.class` (コンパイル済みクラスファイル)
  - `*.jar` (JARファイル、ただし`target/jicoo-reservation-bot-1.0.0.jar`は除外)

