package main.java.mockserver;

import com.google.gson.Gson;
import io.javalin.Javalin;
import java.sql.*;
import io.github.cdimascio.dotenv.Dotenv;

public class MockServer {

    private static Connection getConnection() throws SQLException {
        // Trên Render nó sẽ lấy từ biến môi trường (System.getenv)
        // Dưới local nó sẽ lấy từ file .env
        String dbUrl = System.getenv("DB_URL");
        if (dbUrl == null) {
            Dotenv dotenv = Dotenv.load();
            dbUrl = dotenv.get("DB_URL");
        }
        return DriverManager.getConnection(dbUrl);
    }

    // Hàm tự động tạo bảng products(nếu chưa có)
    private static void initDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS products (" +
                "id SERIAL PRIMARY KEY, " +
                "name VARCHAR(100) NOT NULL, " +
                "price NUMERIC(10, 2) NOT NULL)";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("✅ Đã kiểm tra/tạo bảng products thành công!");
        } catch (SQLException e) {
            System.err.println("❌ Lỗi DB: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        initDatabase(); // Khởi tạo DB trước
        Gson gson = new Gson();

        // Render thường cấp port qua biến môi trường "PORT". Nếu không có thì chạy port
        // 7070.
        int port = System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) : 7070;

        // Khởi động Server
        Javalin app = Javalin.create().start(port);
        System.out.println("🚀 Mock Server đang chạy tại cổng " + port);

        app.get("/", ctx -> ctx.result("Chào mừng đến với Mock Server Đấu Giá!"));

        // 1. GỬI LỆNH: LƯU 1 PRODUCT VÀO DATABASE (POST)
        app.post("/products", ctx -> {
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
            } catch (SQLException e) {
                ctx.status(500).result("Lỗi lưu DB: " + e.getMessage());
            }
        });

        // 2. GỬI LỆNH: LẤY THÔNG TIN PRODUCT (GET)
        app.get("/products/{id}", ctx -> {
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
                    ctx.json(p); // Trả về dạng JSON
                } else {
                    ctx.status(404).result("Không tìm thấy Product với ID " + id);
                }
            }
        });

        // 3. XOÁ PRODUCT (DELETE)
        app.delete("/products/{id}", ctx -> {
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
        });
    }
}