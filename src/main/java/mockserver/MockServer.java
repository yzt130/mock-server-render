package mockserver;

import com.google.gson.Gson;
import io.javalin.Javalin;
import java.sql.*;
import io.github.cdimascio.dotenv.Dotenv;

public class MockServer {

    private static Connection getConnection() throws SQLException {
        String dbUrl = System.getenv("DB_URL");

        if (dbUrl == null || dbUrl.isEmpty()) {
            try {
                Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
                dbUrl = dotenv.get("DB_URL");
            } catch (Exception e) {
                System.err.println("Không load được file .env");
            }
        }

        if (dbUrl == null || dbUrl.isEmpty()) {
            throw new SQLException("Chuỗi DB_URL bị rỗng! Hãy kiểm tra Environment Variables trên Render.");
        }

        System.out.println("Đang thử kết nối tới Database...");
        return DriverManager.getConnection(dbUrl);
    }

    private static void initDatabase() {
        try {
            String sql = "CREATE TABLE IF NOT EXISTS products (" +
                    "id SERIAL PRIMARY KEY, " +
                    "name VARCHAR(100) NOT NULL, " +
                    "price NUMERIC(10, 2) NOT NULL)";
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
                System.out.println("✅ Đã kiểm tra/tạo bảng products thành công!");
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi khởi tạo DB: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // 1. Lấy Port của Render cấp
        int port = 7070;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            port = Integer.parseInt(portEnv);
        }

        // 2. MỞ PORT SERVER TRƯỚC TIÊN (Để Render không báo lỗi No Open Port)
        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false; // Tắt logo cho nhẹ log
        }).start("0.0.0.0", port);

        System.out.println("🚀 Mock Server đã bật thành công tại cổng " + port);

        // 3. SAU KHI SERVER SỐNG, MỚI BẮT ĐẦU KẾT NỐI DB
        initDatabase();

        Gson gson = new Gson();

        // API Test Server
        app.get("/", ctx -> ctx.result("Server Đấu Giá (Mock) đang hoạt động mượt mà!"));

        // LƯU PRODUCT (Có bọc try-catch để trả lỗi thẳng ra màn hình Postman)
        app.post("/products", ctx -> {
            try {
                Product newProduct = gson.fromJson(ctx.body(), Product.class);
                String sql = "INSERT INTO products (name, price) VALUES (?, ?) RETURNING id";
                try (Connection conn = getConnection();
                        PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, newProduct.getName());
                    ps.setDouble(2, newProduct.getPrice());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        newProduct.setId(rs.getInt("id"));
                        ctx.status(201).result("✅ Lưu thành công! ID mới là: " + newProduct.getId());
                    }
                }
            } catch (Exception e) {
                ctx.status(500).result("Lỗi Database: " + e.getMessage());
            }
        });

        // XEM PRODUCT
        app.get("/products/{id}", ctx -> {
            try {
                int id = Integer.parseInt(ctx.pathParam("id"));
                String sql = "SELECT * FROM products WHERE id = ?";
                try (Connection conn = getConnection();
                        PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, id);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        Product p = new Product();
                        p.setId(rs.getInt("id"));
                        p.setName(rs.getString("name"));
                        p.setPrice(rs.getDouble("price"));
                        ctx.json(p);
                    } else {
                        ctx.status(404).result("Không tìm thấy Product ID: " + id);
                    }
                }
            } catch (Exception e) {
                ctx.status(500).result("Lỗi Database: " + e.getMessage());
            }
        });

        // XOÁ PRODUCT
        app.delete("/products/{id}", ctx -> {
            try {
                int id = Integer.parseInt(ctx.pathParam("id"));
                String sql = "DELETE FROM products WHERE id = ?";
                try (Connection conn = getConnection();
                        PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, id);
                    int rows = ps.executeUpdate();
                    if (rows > 0) {
                        ctx.result("✅ Đã xóa thành công Product ID: " + id);
                    } else {
                        ctx.status(404).result("Không tìm thấy Product để xóa!");
                    }
                }
            } catch (Exception e) {
                ctx.status(500).result("Lỗi Database: " + e.getMessage());
            }
        });
    }
}