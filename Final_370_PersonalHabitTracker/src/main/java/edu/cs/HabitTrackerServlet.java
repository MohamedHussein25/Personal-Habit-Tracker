package edu.cs;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.util.*;

@WebServlet("/HabitTrackerServlet")
public class HabitTrackerServlet extends HttpServlet {
    private static final String DB_URL = "jdbc:mysql://207.38.227.166/hamzadb?useSSL=false";
    private static final String DB_USER = "hmz25";
    private static final String DB_PASSWORD = "hmz1234";

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String action = request.getParameter("action");
        HttpSession session = request.getSession();

        if ("signup".equals(action)) {
            String username = request.getParameter("username");
            String password = request.getParameter("password");

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                PreparedStatement check = conn.prepareStatement("SELECT * FROM users WHERE username = ?");
                check.setString(1, username);
                ResultSet rs = check.executeQuery();
                if (rs.next()) {
                    response.getWriter().println("Username already exists.");
                    return;
                }
                PreparedStatement insert = conn.prepareStatement("INSERT INTO users (username, password) VALUES (?, ?)");
                insert.setString(1, username);
                insert.setString(2, password);
                insert.executeUpdate();
                session.setAttribute("username", username);
                response.sendRedirect("index.html");
            } catch (SQLException e) {
                e.printStackTrace();
                response.getWriter().println("Database error.");
            }

        } else if ("login".equals(action)) {
            String username = request.getParameter("username");
            String password = request.getParameter("password");

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE username = ? AND password = ?");
                stmt.setString(1, username);
                stmt.setString(2, password);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    session.setAttribute("username", username);
                    response.sendRedirect("index.html");
                } else {
                    response.getWriter().println("Invalid login.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                response.getWriter().println("Database error.");
            }

        } else if (session.getAttribute("username") == null) {
            response.getWriter().println("You must be logged in.");

        } else {
            String username = (String) session.getAttribute("username");
            String name = request.getParameter("name");

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                if ("add".equals(action)) {
                    int goal = Integer.parseInt(request.getParameter("goal"));
                    String type = request.getParameter("type");
                    PreparedStatement insert = conn.prepareStatement("INSERT INTO habits (username, name, goal, type) VALUES (?, ?, ?, ?)");
                    insert.setString(1, username);
                    insert.setString(2, name);
                    insert.setInt(3, goal);
                    insert.setString(4, type);
                    insert.executeUpdate();
                } else if ("edit".equals(action)) {
                    int goal = Integer.parseInt(request.getParameter("goal"));
                    String type = request.getParameter("type");
                    PreparedStatement update = conn.prepareStatement("UPDATE habits SET goal = ?, type = ? WHERE username = ? AND name = ?");
                    update.setInt(1, goal);
                    update.setString(2, type);
                    update.setString(3, username);
                    update.setString(4, name);
                    update.executeUpdate();
                } else if ("delete".equals(action)) {
                    PreparedStatement delete = conn.prepareStatement("DELETE FROM habits WHERE username = ? AND name = ?");
                    delete.setString(1, username);
                    delete.setString(2, name);
                    delete.executeUpdate();
                }
                response.sendRedirect("index.html");
            } catch (SQLException e) {
                e.printStackTrace();
                response.getWriter().println("Database error.");
            }
        }
    }
} 
