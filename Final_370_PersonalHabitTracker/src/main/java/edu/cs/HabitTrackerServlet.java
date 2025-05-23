<<<<<<< HEAD
package edu.cs;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.servlet.*;
import java.io.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.time.temporal.TemporalAdjusters;
import org.json.JSONArray;
import org.json.JSONObject;

@WebServlet(urlPatterns = {"/HabitTrackerServlet/*", "/progress-data"})
public class HabitTrackerServlet extends HttpServlet {
    private static final String DB_URL = "jdbc:mysql://207.38.227.166/htrackerdb?useSSL=false";
    private static final String DB_USER = "hmz25";
    private static final String DB_PASSWORD = "hmz1234";

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo(); // e.g. "/signup", "/login"
        HttpSession session = request.getSession();
        response.setContentType("text/html;charset=UTF-8");

        try {
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
                    response.sendRedirect(request.getContextPath() + "/Dashboard.html");

                } else if ("/login".equals(path)) {
                    String email = request.getParameter("email");
                    String password = request.getParameter("password");

                    PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE email = ? AND password = ?");
                    stmt.setString(1, email);
                    stmt.setString(2, password);
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        session.setAttribute("username", rs.getString("username"));
                        response.sendRedirect(request.getContextPath() + "/Dashboard.html");
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

                    } else if ("checkin".equalsIgnoreCase(action)) {
                        PreparedStatement checkin = conn.prepareStatement(
                            "INSERT INTO habit_checkins (username, habit_name, checkin_date, completed) VALUES (?, ?, CURDATE(), true)"
                        );
                        checkin.setString(1, username);
                        checkin.setString(2, name);
                        checkin.executeUpdate();

                        PreparedStatement streakQuery = conn.prepareStatement(
                            "SELECT checkin_date FROM habit_checkins WHERE username = ? AND habit_name = ? AND completed = true ORDER BY checkin_date DESC"
                        );
                        streakQuery.setString(1, username);
                        streakQuery.setString(2, name);
                        ResultSet streakRs = streakQuery.executeQuery();

                        int streak = 0;
                        LocalDate today = LocalDate.now();
                        while (streakRs.next()) {
                            LocalDate date = streakRs.getDate("checkin_date").toLocalDate();
                            if (date.equals(today.minusDays(streak))) {
                                streak++;
                            } else {
                                break;
                            }
                        }

                        if (streak == 7) {
                            PreparedStatement badgeStmt = conn.prepareStatement(
                                "INSERT IGNORE INTO user_badges (username, badge_name) VALUES (?, '7-Day Streak')"
                            );
                            badgeStmt.setString(1, username);
                            badgeStmt.executeUpdate();
                        }
                    }

                    response.sendRedirect(request.getContextPath() + "/Dashboard.html");
                }
            }
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
            response.getWriter().println("Server error: " + e.getMessage());
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if ("/progress-data".equals(request.getServletPath())) {
            HttpSession session = request.getSession();
            String username = (String) session.getAttribute("username");
            String habit = request.getParameter("habit");

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            JSONObject json = new JSONObject();
            JSONArray labels = new JSONArray();
            JSONArray data = new JSONArray();

            if (username == null || habit == null || habit.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            try {
                Class.forName("com.mysql.cj.jdbc.Driver");

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    LocalDate today = LocalDate.now();
                    DayOfWeek firstDayOfWeek = DayOfWeek.SUNDAY;
                    LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek));

                    Map<LocalDate, Boolean> checkins = new HashMap<>();
                    PreparedStatement stmt = conn.prepareStatement(
                        "SELECT checkin_date FROM habit_checkins WHERE username = ? AND habit_name = ? AND checkin_date BETWEEN ? AND ? AND completed = true"
                    );
                    stmt.setString(1, username);
                    stmt.setString(2, habit);
                    stmt.setDate(3, java.sql.Date.valueOf(weekStart));
                    stmt.setDate(4, java.sql.Date.valueOf(weekStart.plusDays(6)));

                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        LocalDate date = rs.getDate("checkin_date").toLocalDate();
                        checkins.put(date, true);
                    }

                    for (int i = 0; i < 7; i++) {
                        LocalDate date = weekStart.plusDays(i);
                        labels.put(date.getDayOfWeek().toString().substring(0, 3));
                        data.put(checkins.getOrDefault(date, false) ? 1 : 0);
                    }

                    json.put("labels", labels);
                    json.put("data", data);
                }
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                json.put("error", e.getMessage());
            }

            PrintWriter out = response.getWriter();
            out.print(json);
            out.flush();
        }
    }
=======
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
>>>>>>> branch 'main' of https://github.com/MohamedHussein25/Personal-Habit-Tracker
}