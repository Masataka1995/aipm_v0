# 初期設定の状態

## ✅ 完了した項目

1. **基本ディレクトリ構造の作成**

   - Flow/
   - Stock/
   - Archived/
   - .cursor/rules/
   - scripts/

2. **Flow 内の日付フォルダ作成**

   - Flow/202511/2025-11-18/

3. **VSCode 設定ファイルの作成**
   - .vscode/settings.json（Marp テーマ設定）

## ⚠️ 未完了の項目（git のインストールが必要）

以下のリポジトリをクローンするには、git のインストールが必要です：

1. **ルールリポジトリ**

   - URL: https://github.com/miyatti777/rules_basic_public.git
   - ターゲット: .cursor/rules/basic
   - コマンド: `git clone https://github.com/miyatti777/rules_basic_public.git .cursor/rules/basic`

2. **スクリプトリポジトリ**
   - URL: https://github.com/miyatti777/scripts_public.git
   - ターゲット: scripts
   - コマンド: `git clone https://github.com/miyatti777/scripts_public.git scripts`

## 次のステップ

### 1. Git のインストール（未インストールの場合）

Windows 環境では、以下のいずれかの方法で git をインストールできます：

- **Git for Windows**: https://git-scm.com/download/win
- **GitHub Desktop**: https://desktop.github.com/

### 2. リポジトリのクローン

git をインストール後、以下のコマンドを実行してください：

```powershell
# ルールリポジトリのクローン
git clone https://github.com/miyatti777/rules_basic_public.git .cursor/rules/basic

# スクリプトリポジトリのクローン
git clone https://github.com/miyatti777/scripts_public.git scripts
```

### 3. ユーザー設定ファイルの作成（オプション）

タスク管理機能を使用する場合、以下のファイルを作成してください：

- `scripts/config/user_config.yaml`
- 内容は README.md の「ユーザー設定（タスク管理用）」セクションを参照

### 4. Cursor のユーザールール設定

Cursor の設定からユーザールールを設定してください：

1. Cursor の右上にある歯車アイコン（⚙️）をクリック
2. 「Settings」から「Rules」を選択
3. README.md の「User Rules」セクションの内容をコピーして貼り付け

## 確認事項

- [ ] Git がインストールされている
- [ ] ルールリポジトリがクローンされている（.cursor/rules/basic/.git が存在する）
- [ ] スクリプトリポジトリがクローンされている（scripts/.git が存在する）
- [ ] Cursor のユーザールールが設定されている

## トラブルシューティング

### リポジトリのクローンに失敗する場合

1. git が正しくインストールされているか確認: `git --version`
2. インターネット接続を確認
3. ターゲットディレクトリが空であることを確認（既存のファイルがある場合は削除またはバックアップ）

### ルールファイルが読み込まれない場合

1. `.cursor/rules/basic/` ディレクトリにルールファイル（\*.mdc）が存在するか確認
2. Cursor を再起動
3. チャットで `@00_master_rules.mdc` のように明示的にルールを指定
