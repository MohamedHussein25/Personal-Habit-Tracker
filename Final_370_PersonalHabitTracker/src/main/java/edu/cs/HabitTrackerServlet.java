package edu.cs;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.*;
import java.sql.*;

@WebServlet("/HabitTrackerServlet/*")
public class HabitTrackerServlet extends HttpServlet {
    private static final String DB_URL = "jdbc:mysql://207.38.227.166/htrackerdb?useSSL=false";
    private static final String DB_USER = "hmz25";
    private static final String DB_PASSWORD = "hmz1234";

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo(); // e.g. "/signup", "/login"
        HttpSession session = request.getSession();
        response.setContentType("text/html;charset=UTF-8");

        try {
            // Force-load MySQL JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                if ("/signup".equals(path)) {
                    String name = request.getParameter("name");
                    String email = request.getParameter("email");
                    String password = request.getParameter("password");

                    PreparedStatement check = conn.prepareStatement("SELECT * FROM users WHERE email = ?");
                    check.setString(1, email);
                    ResultSet rs = check.executeQuery();

                    if (rs.next()) {
                        response.getWriter().println("Email already exists.");
                        return;
                    }

                    PreparedStatement insert = conn.prepareStatement("INSERT INTO users (username, email, password) VALUES (?, ?, ?)");
                    insert.setString(1, name);
                    insert.setString(2, email);
                    insert.setString(3, password);
                    insert.executeUpdate();

                    session.setAttribute("username", name);
                    response.sendRedirect("Homepage.html");

                } else if ("/login".equals(path)) {
                    String email = request.getParameter("email");
                    String password = request.getParameter("password");

                    PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE email = ? AND password = ?");
                    stmt.setString(1, email);
                    stmt.setString(2, password);
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        session.setAttribute("username", rs.getString("username"));
                        response.sendRedirect("Homepage.html");
                    } else {
                        response.getWriter().println("Invalid email or password.");
                    }

                } else {
                    String action = request.getParameter("action");
                    String username = (String) session.getAttribute("username");

                    if (username == null) {
                        response.sendRedirect("Login.html");
                        return;
                    }

                    String name = request.getParameter("name");

                    if ("add".equalsIgnoreCase(action)) {
                        int goal = Integer.parseInt(request.getParameter("goal"));
                        String type = request.getParameter("type");
                        PreparedStatement insert = conn.prepareStatement("INSERT INTO habits (username, name, goal, type) VALUES (?, ?, ?, ?)");
                        insert.setString(1, username);
                        insert.setString(2, name);
                        insert.setInt(3, goal);
                        insert.setString(4, type);
                        insert.executeUpdate();

                    } else if ("edit".equalsIgnoreCase(action)) {
                        int goal = Integer.parseInt(request.getParameter("goal"));
                        String type = request.getParameter("type");
                        PreparedStatement update = conn.prepareStatement("UPDATE habits SET goal = ?, type = ? WHERE username = ? AND name = ?");
                        update.setInt(1, goal);
                        update.setString(2, type);
                        update.setString(3, username);
                        update.setString(4, name);
                        update.executeUpdate();

                    } else if ("delete".equalsIgnoreCase(action)) {
                        PreparedStatement delete = conn.prepareStatement("DELETE FROM habits WHERE username = ? AND name = ?");
                        delete.setString(1, username);
                        delete.setString(2, name);
                        delete.executeUpdate();
                    }

                    response.sendRedirect("Homepage.html");
                }
            }
        } catch (ClassNotFoundException e) {
            response.getWriter().println("JDBC Driver not found: " + e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            response.getWriter().println("Database error: " + e.getMessage());
        }
    }
}