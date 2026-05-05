package com.synchplay;

import com.synchplay.server.SynchPlayServer;

/**
 * Web 服务入口
 * 启动 HTTP 服务器，提供 REST API 和前端界面
 */
public class ServerMain {
    public static void main(String[] args) {
        int port = 8080;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }

        try {
            SynchPlayServer server = new SynchPlayServer(port);
            server.start();

            System.out.println("按 Ctrl+C 停止服务\n");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n服务已停止");
            }));

            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("启动失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
