package com.jicoo.bot.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 静的ファイル配信サーブレット
 */
public class StaticFileServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(StaticFileServlet.class);
    private static final String WEBAPP_DIR = "src/main/webapp";
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String requestURI = req.getRequestURI();
        String pathInfo = req.getPathInfo();
        String servletPath = req.getServletPath();
        
        logger.info("静的ファイルリクエスト: URI={}, PathInfo={}, ServletPath={}, QueryString={}", 
            requestURI, pathInfo, servletPath, req.getQueryString());
        
        // WebSocketパスとAPIパスは処理しない（既に他のハンドラーで処理されているはず）
        if (requestURI != null && (requestURI.startsWith("/ws") || requestURI.startsWith("/api/"))) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            logger.debug("静的ファイルサーブレットがWebSocket/APIパスを無視しました: {}", requestURI);
            return;
        }
        
        // パスを取得
        // Jettyで"/"にマッピングされたサーブレットの場合、requestURIから直接抽出するのが最も確実
        String path = null;
        
        // まず、pathInfoを確認（Jettyで"/"にマッピングされた場合、pathInfoに実際のパスが入る）
        if (pathInfo != null && !pathInfo.isEmpty() && !pathInfo.equals("/")) {
            path = pathInfo;
            logger.info("✅ pathInfoからパスを取得: {}", path);
        }
        
        // pathInfoが利用できない場合、requestURIから抽出
        if (path == null && requestURI != null) {
            path = requestURI;
            // クエリパラメータを除去
            int queryIndex = path.indexOf('?');
            if (queryIndex >= 0) {
                path = path.substring(0, queryIndex);
            }
            // コンテキストパスを除去
            String contextPath = req.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
                path = path.substring(contextPath.length());
                // パスが空になった場合は"/"にする
                if (path.isEmpty()) {
                    path = "/";
                }
            }
            logger.info("✅ requestURIからパスを抽出: {} (元のURI: {}, コンテキストパス: {})", path, requestURI, contextPath);
        }
        
        // それでも取得できない場合、servletPathを確認（フォールバック）
        if ((path == null || path.equals("/")) && servletPath != null && !servletPath.equals("/") && !servletPath.isEmpty()) {
            path = servletPath;
            // /webapp/プレフィックスを除去
            if (path.startsWith("/webapp/")) {
                path = path.substring("/webapp".length());
            }
            logger.info("✅ servletPathからパスを取得（フォールバック）: {}", path);
        }
        
        // requestURIから取得したパスにも/webapp/プレフィックスが含まれている可能性があるため除去
        if (path != null && path.startsWith("/webapp/")) {
            path = path.substring("/webapp".length());
            logger.info("✅ /webapp/プレフィックスを除去: {}", path);
        }
        
        // 静的ファイルの拡張子をチェック
        boolean isStaticFile = false;
        if (path != null && !path.isEmpty() && !path.equals("/")) {
            String lowerPath = path.toLowerCase();
            isStaticFile = lowerPath.matches(".*\\.(js|css|png|jpg|jpeg|gif|svg|ico|json|woff|woff2|ttf|eot)$");
        }
        
        // ルートパスの場合はindex.htmlを返す
        // ただし、静的ファイル（.js, .css, .png等）のリクエストはそのまま処理する
        if (path == null || path.equals("/") || path.isEmpty()) {
            if (!isStaticFile) {
                path = "/index.html";
                logger.info("✅ ルートパスのためindex.htmlに設定");
            }
        } else if (!path.equals("/index.html") && path.endsWith("/")) {
            // パスが"/"で終わる場合（例："/dir/"）、index.htmlを追加
            // ただし、静的ファイルの拡張子が付いている場合はそのまま処理する
            if (!isStaticFile) {
                path = path + "index.html";
                logger.info("✅ ディレクトリパスのためindex.htmlを追加: {}", path);
            }
        }
        
        // 先頭のスラッシュを確保
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        
        // セキュリティ: パストラバーサル対策
        if (path.contains("..")) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            logger.warn("パストラバーサル攻撃の可能性: {}", path);
            return;
        }
        
        logger.info("解決パス: {}", path);
        
        // ファイルを読み込む（JAR実行時と開発時の両方に対応）
        java.io.InputStream inputStream = null;
        String workingDir = System.getProperty("user.dir");
        
        // まず、ファイルシステムから読み込む（開発時を優先）
        // パスの先頭のスラッシュを除去してファイルパスを構築
        String filePathStr = path.startsWith("/") ? path.substring(1) : path;
        // パスを正規化（Windowsのパス区切り文字に対応）
        filePathStr = filePathStr.replace('/', File.separatorChar);
        
        // 複数の可能なパスを試行
        Path[] possiblePaths = {
            Paths.get(workingDir, WEBAPP_DIR, filePathStr).normalize(),
            Paths.get(workingDir, "target", "classes", "webapp", filePathStr).normalize(),
            Paths.get(workingDir, "target", "classes", WEBAPP_DIR, filePathStr).normalize()
        };
        
        File file = null;
        for (Path filePath : possiblePaths) {
            File testFile = filePath.toFile();
            if (testFile.exists() && testFile.isFile() && testFile.canRead()) {
                file = testFile;
                logger.info("ファイルパス解決: リクエストURI={}, 解決パス={}, ファイルパス={}, 存在={}, ファイル={}, ディレクトリ={}, 読み取り可能={}", 
                    requestURI, path, filePath, file.exists(), file.isFile(), file.isDirectory(), file.canRead());
                break;
            }
        }
        
        if (file == null) {
            // 最後のパスをログに記録
            Path filePath = possiblePaths[0];
            file = filePath.toFile();
            logger.info("ファイルパス解決: リクエストURI={}, 解決パス={}, ファイルパス={}, 存在={}, ファイル={}, ディレクトリ={}, 読み取り可能={}", 
                requestURI, path, filePath, file.exists(), file.isFile(), file.isDirectory(), file.canRead());
        }
        
        if (file != null && file.exists() && file.isFile() && file.canRead()) {
            try {
                inputStream = new FileInputStream(file);
                logger.info("✅ 静的ファイルを配信（ファイルシステム）: {} -> {} (サイズ: {} bytes)", 
                    requestURI, file.getAbsolutePath(), file.length());
            } catch (IOException e) {
                logger.error("ファイル読み込みエラー: {} (エラー: {})", file.getAbsolutePath(), e.getMessage(), e);
            }
        } else if (file != null && file.exists() && file.isDirectory()) {
            logger.warn("⚠️ パスはディレクトリです（ファイルではありません）: {}", file.getAbsolutePath());
        } else if (file != null && file.exists() && !file.canRead()) {
            logger.warn("⚠️ ファイルは存在しますが読み取りできません: {}", file.getAbsolutePath());
        } else {
            logger.warn("⚠️ ファイルが存在しません: {} (作業ディレクトリ: {})", possiblePaths[0], workingDir);
        }
        
        // ファイルシステムから読み込めない場合、JAR内のリソースとして読み込む
        if (inputStream == null) {
            // リソースパスは "webapp" + path の形式（例: "webapp/bundle.js"）
            String resourcePath = "webapp" + path;
            logger.info("リソースパスを試行: {}", resourcePath);
            inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
            
            if (inputStream != null) {
                logger.info("✅ 静的ファイルを配信（JARリソース）: {} -> {}", requestURI, resourcePath);
            } else {
                // 別のパス形式も試す（先頭スラッシュ付き）
                String resourcePath2 = "/webapp" + path;
                logger.info("リソースパスを試行（スラッシュ付き）: {}", resourcePath2);
                inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath2);
                if (inputStream != null) {
                    logger.info("✅ 静的ファイルを配信（JARリソース、スラッシュ付き）: {} -> {}", requestURI, resourcePath2);
                } else {
                    // パスの先頭スラッシュを除去して再試行
                    String resourcePath3 = "webapp" + (path.startsWith("/") ? path : "/" + path);
                    logger.info("リソースパスを試行（調整後）: {}", resourcePath3);
                    inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath3);
                    if (inputStream != null) {
                        logger.info("✅ 静的ファイルを配信（JARリソース、調整後）: {} -> {}", requestURI, resourcePath3);
                    } else {
                        logger.warn("⚠️ すべてのリソースパス形式でファイルが見つかりませんでした: {}", path);
                    }
                }
            }
        }
        
        if (inputStream == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            
            // 試行したリソースパスをログに記録
            String attemptedResourcePaths = String.format("webapp%s, /webapp%s, webapp/%s", 
                path, path, filePathStr);
            
            // ファイルパス情報を取得
            String filePathInfo = file != null ? file.getAbsolutePath() : possiblePaths[0].toString();
            boolean fileExists = file != null && file.exists();
            
            // パスの拡張子を確認（大文字小文字を区別しない）
            String lowerPath = path != null ? path.toLowerCase() : "";
            
            // JavaScriptファイルの場合は空のJavaScriptを返す（エラーを避けるため）
            if (lowerPath.endsWith(".js")) {
                resp.setContentType("application/javascript; charset=UTF-8");
                resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                resp.setHeader("Pragma", "no-cache");
                resp.setDateHeader("Expires", 0);
                resp.getWriter().write("// File not found: " + path + "\n// Please check server logs for details.\nconsole.error('File not found: " + path + "');");
                logger.error("❌ JavaScriptファイルが見つかりません: {} (試行したリソースパス: {}, ファイルパス: {}, 作業ディレクトリ: {}, ファイル存在: {})", 
                    path, attemptedResourcePaths, filePathInfo, workingDir, fileExists);
            } else if (lowerPath.endsWith(".css")) {
                resp.setContentType("text/css; charset=UTF-8");
                resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                resp.setHeader("Pragma", "no-cache");
                resp.setDateHeader("Expires", 0);
                resp.getWriter().write("/* File not found: " + path + " */");
                logger.error("❌ CSSファイルが見つかりません: {} (試行したリソースパス: {}, ファイルパス: {}, 作業ディレクトリ: {}, ファイル存在: {})", 
                    path, attemptedResourcePaths, filePathInfo, workingDir, fileExists);
            } else {
                resp.setContentType("text/html; charset=UTF-8");
                // HTMLエラーページを返す
                String errorHtml = "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>File Not Found</title></head>" +
                    "<body><h1>404 - File Not Found</h1><p>File: " + path + "</p>" +
                    "<p>Request URI: " + requestURI + "</p>" +
                    "<p>Attempted Resource Paths: " + attemptedResourcePaths + "</p>" +
                    "<p>File Path: " + filePathInfo + "</p>" +
                    "<p>Working Directory: " + workingDir + "</p>" +
                    "<p>File Exists: " + fileExists + "</p>" +
                    "<p>Please check server logs for more details.</p></body></html>";
                resp.getWriter().write(errorHtml);
                logger.error("❌ ファイルが見つかりません: {} (試行したリソースパス: {}, ファイルパス: {}, 作業ディレクトリ: {}, ファイル存在: {})", 
                    path, attemptedResourcePaths, filePathInfo, workingDir, fileExists);
            }
            return;
        }
        
        // Content-Typeを設定
        String contentType = getContentType(path);
        resp.setContentType(contentType);
        resp.setCharacterEncoding("UTF-8");
        
        // キャッシュ制御ヘッダーを設定
        // HTMLとJavaScriptはキャッシュしない（開発時）
        if (path.endsWith(".html") || path.endsWith(".js")) {
            resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            resp.setHeader("Pragma", "no-cache");
            resp.setDateHeader("Expires", 0);
        } else {
            // その他の静的ファイルは1時間キャッシュ
            resp.setHeader("Cache-Control", "public, max-age=3600");
        }
        
        // ファイルを読み込んで送信
        final java.io.InputStream finalInputStream = inputStream;
        long totalBytesRead = 0;
        try (finalInputStream; OutputStream out = resp.getOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = finalInputStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            out.flush();
            logger.info("✅ ファイル送信完了: {} (Content-Type: {}, 送信サイズ: {} bytes)", 
                path, contentType, totalBytesRead);
        } catch (IOException e) {
            logger.error("ファイル送信中にエラーが発生しました: {}", path, e);
            // 既にレスポンスが送信されている可能性があるため、エラーをログに記録するのみ
        }
    }
    
    private String getContentType(String path) {
        if (path.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        } else if (path.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        } else if (path.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        } else if (path.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        } else if (path.endsWith(".png")) {
            return "image/png";
        } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (path.endsWith(".gif")) {
            return "image/gif";
        } else if (path.endsWith(".svg")) {
            return "image/svg+xml";
        } else {
            return "application/octet-stream";
        }
    }
}
