package org.example;
//------------------------------
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.Random;
//------------------------------
public class Djakstra extends JFrame {
    private List<Ball> balls = new ArrayList<>();
    private Set<DischargePath> activeDischarges = new HashSet<>();
    private List<Barrier> barriers = new ArrayList<>();
    private static final int MAX_CONNECTION_DISTANCE = 170;
    private static final int SQUARE_SIZE = 20;
    private static final int SQUARE_MARGIN = 10;
    private static final long DISCHARGE_DURATION = 500;
    private static final int SPEED = 0;
    private Timer gameTimer;
    private List<Point> squareCenters = new ArrayList<>();
    private Random random = new Random();

    public Djakstra() {
        setTitle("Dijkstra");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                setBackground(Color.WHITE);
                squareCenters.clear();
                squareCenters.add(new Point(SQUARE_MARGIN + SQUARE_SIZE/2, SQUARE_MARGIN + SQUARE_SIZE/2));
                squareCenters.add(new Point(getWidth() - SQUARE_SIZE/2 - SQUARE_MARGIN, SQUARE_MARGIN + SQUARE_SIZE/2));
                squareCenters.add(new Point(SQUARE_MARGIN + SQUARE_SIZE/2, getHeight() - SQUARE_SIZE/2 - SQUARE_MARGIN));
                squareCenters.add(new Point(getWidth() - SQUARE_SIZE/2 - SQUARE_MARGIN, getHeight() - SQUARE_SIZE/2 - SQUARE_MARGIN));

                g.setColor(Color.GREEN);
                for (Point center : squareCenters) {
                    g.fillRect(
                            center.x - SQUARE_SIZE/2,
                            center.y - SQUARE_SIZE/2,
                            SQUARE_SIZE,
                            SQUARE_SIZE
                    );
                }

                g.setColor(Color.BLACK);
                for (Barrier barrier : barriers) {
                    g.fillRect(barrier.x, barrier.y, barrier.width, barrier.height);
                }

                g.setColor(new Color(0, 0, 255, 200));
                drawDischargePaths(g);

                for (Ball ball : balls) {
                    g.setColor(ball.color);
                    g.fillOval(ball.x - ball.radius, ball.y - ball.radius, ball.radius * 2, ball.radius * 2);
                }

                g.setColor(new Color(255, 0, 0, 200));
                for (DischargePath discharge : activeDischarges) {
                    if (discharge.pathExists) {
                        Point start = discharge.getCurrentStartPoint();
                        g.fillOval(start.x - 8, start.y - 8, 16, 16);
                    }
                }
            }

            private void drawDischargePaths(Graphics g) {
                for (DischargePath discharge : activeDischarges) {
                    if (discharge.pathExists) {
                        Point currentStart = discharge.getCurrentStartPoint();
                        List<Point> currentPath = discharge.getCurrentPath();
                        Point targetSquare = discharge.targetSquare;

                        Point prev = currentStart;
                        for (Point next : currentPath) {
                            g.drawLine(prev.x, prev.y, next.x, next.y);
                            prev = next;
                        }
                        if (prev != null && targetSquare != null) {
                            g.drawLine(prev.x, prev.y, targetSquare.x, targetSquare.y);
                        }
                    }
                }
            }
        };

        gameTimer = new Timer(16, e -> {
            long currentTime = System.currentTimeMillis();
            boolean needsRepaint = false;

            for (Ball ball : balls) {
                ball.updatePosition(panel.getWidth(), panel.getHeight());
            }

            for (DischargePath discharge : activeDischarges) {
                discharge.updatePath(balls, barriers);
            }

            Iterator<DischargePath> it = activeDischarges.iterator();
            while (it.hasNext()) {
                DischargePath discharge = it.next();
                if (currentTime - discharge.time > DISCHARGE_DURATION) {
                    it.remove();
                    needsRepaint = true;
                }
            }

            panel.repaint();
        });
        gameTimer.start();

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    int radius = 15;
                    int speedX = SPEED == 0 ? 0 : random.nextInt(SPEED) + 1;
                    int speedY = SPEED == 0 ? 0 : random.nextInt(SPEED) + 1;
                    if (random.nextBoolean()) speedX = -speedX;
                    if (random.nextBoolean()) speedY = -speedY;
                    Color color = new Color(135, 206, 250);
                    balls.add(new Ball(e.getX(), e.getY(), radius, speedX, speedY, color));
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    Ball nearest = findNearestBall(e.getPoint());
                    if (nearest != null) {
                        for (Point square : squareCenters) {
                            List<Ball> pathBalls = findShortestPath(nearest, square);
                            if (pathBalls != null) {
                                activeDischarges.add(new DischargePath(nearest, pathBalls, square));
                            } else {
                                if (nearest.distance(square) <= MAX_CONNECTION_DISTANCE &&
                                        !lineIntersectsBarrier(new Point(nearest.x, nearest.y), square)) {
                                    activeDischarges.add(new DischargePath(nearest, Collections.emptyList(), square));
                                }
                            }
                        }
                    }
                }
                panel.repaint();
            }

            private Ball findNearestBall(Point point) {
                return balls.stream()
                        .min(Comparator.comparingDouble(b -> b.distance(point)))
                        .orElse(null);
            }

            private List<Ball> findShortestPath(Ball start, Point targetSquare) {
                Map<Ball, List<Ball>> graph = new HashMap<>();
                Map<Ball, Double> distances = new HashMap<>();
                Map<Ball, Ball> previous = new HashMap<>();
                PriorityQueue<Ball> queue = new PriorityQueue<>(Comparator.comparingDouble(distances::get));
                for (Ball ball : balls) {
                    distances.put(ball, Double.MAX_VALUE);
                    previous.put(ball, null);
                    graph.put(ball, new ArrayList<>());
                }
                distances.put(start, 0.0);
                queue.add(start);

                //Build graph
                for (Ball ball1 : balls) {
                    for (Ball ball2 : balls) {
                        if (!ball1.equals(ball2)) {
                            double distance = ball1.distance(ball2);
                            if (distance <= MAX_CONNECTION_DISTANCE &&
                                    !lineIntersectsBarrier(new Point(ball1.x, ball1.y), new Point(ball2.x, ball2.y))) {
                                graph.get(ball1).add(ball2);
                            }
                        }
                    }
                }

                // Dijkstra realisation
                while (!queue.isEmpty()) {
                    Ball current = queue.poll();
                    if (current.distance(targetSquare) <= MAX_CONNECTION_DISTANCE &&
                            !lineIntersectsBarrier(new Point(current.x, current.y), targetSquare)) {
                        return reconstructPath(previous, current);
                    }
                    for (Ball neighbor : graph.get(current)) {
                        double alt = distances.get(current) + current.distance(neighbor);
                        if (alt < distances.get(neighbor)) {  //find
                            distances.put(neighbor, alt);
                            previous.put(neighbor, current);
                            queue.add(neighbor);
                        }
                    }
                }

                return null;
            }

            private boolean lineIntersectsBarrier(Point p1, Point p2) {
                for (Barrier barrier : barriers) {
                    if (lineIntersectsRectangle(p1, p2, barrier)) {
                        return true;
                    }
                }
                return false;
            }

            private boolean lineIntersectsRectangle(Point p1, Point p2, Barrier rect) {
                return lineIntersectsLine(p1, p2, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y)) ||
                        lineIntersectsLine(p1, p2, new Point(rect.x + rect.width, rect.y), new Point(rect.x + rect.width, rect.y + rect.height)) ||
                        lineIntersectsLine(p1, p2, new Point(rect.x + rect.width, rect.y + rect.height), new Point(rect.x, rect.y + rect.height)) ||
                        lineIntersectsLine(p1, p2, new Point(rect.x, rect.y + rect.height), new Point(rect.x, rect.y)) ||
                        (rect.contains(p1) || rect.contains(p2));
            }

            private boolean lineIntersectsLine(Point p1, Point p2, Point p3, Point p4) {
                int d1 = direction(p3, p4, p1);
                int d2 = direction(p3, p4, p2);
                int d3 = direction(p1, p2, p3);
                int d4 = direction(p1, p2, p4);

                if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
                        ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
                    return true;
                }

                if (d1 == 0 && onSegment(p3, p4, p1)) return true;
                if (d2 == 0 && onSegment(p3, p4, p2)) return true;
                if (d3 == 0 && onSegment(p1, p2, p3)) return true;
                if (d4 == 0 && onSegment(p1, p2, p4)) return true;

                return false;
            }

            private int direction(Point a, Point b, Point c) {
                return (b.x - a.x)*(c.y - a.y) - (b.y - a.y)*(c.x - a.x);
            }

            private boolean onSegment(Point a, Point b, Point c) {
                return Math.min(a.x, b.x) <= c.x && c.x <= Math.max(a.x, b.x) &&
                        Math.min(a.y, b.y) <= c.y && c.y <= Math.max(a.y, b.y);
            }

            private List<Ball> reconstructPath(Map<Ball, Ball> previous, Ball current) {
                List<Ball> path = new ArrayList<>();
                while (previous.get(current) != null) {
                    path.add(0, current);
                    current = previous.get(current);
                }
                return path;
            }
        });

        panel.setFocusable(true);
        panel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_K) {
                    Point mousePos = MouseInfo.getPointerInfo().getLocation();
                    SwingUtilities.convertPointFromScreen(mousePos, panel);
                    barriers.add(new Barrier(mousePos.x - 25, mousePos.y - 5, 50, 10));
                    panel.repaint();
                }
            }
        });

        add(panel);
    }

    private class Ball {
        int x, y;
        int radius;
        int speedX, speedY;
        Color color;

        public Ball(int x, int y, int radius, int speedX, int speedY, Color color) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.speedX = speedX;
            this.speedY = speedY;
            this.color = color;
        }

        public void updatePosition(int width, int height) {
            if (speedX == 0 && speedY == 0) {
                return;
            }

            x += speedX;
            y += speedY;

            if (x - radius <= 0 || x + radius >= width) {
                speedX = -speedX;
                x = Math.max(radius, Math.min(x, width - radius));
            }
            if (y - radius <= 0 || y + radius >= height) {
                speedY = -speedY;
                y = Math.max(radius, Math.min(y, height - radius));
            }
        }

        public double distance(Point p) {
            return Math.sqrt((x - p.x) * (x - p.x) + (y - p.y) * (y - p.y));
        }

        public double distance(Ball b) {
            return Math.sqrt((x - b.x) * (x - b.x) + (y - b.y) * (y - b.y));
        }
    }

    private class Barrier {
        int x, y;
        int width, height;

        public Barrier(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public boolean contains(Point p) {
            return p.x >= x && p.x <= x + width && p.y >= y && p.y <= y + height;
        }
    }

    private class DischargePath {
        private Ball startBall;
        private List<Ball> pathBalls;
        private Point targetSquare;
        boolean pathExists;
        long time = System.currentTimeMillis();

        DischargePath(Ball startBall, List<Ball> pathBalls, Point targetSquare) {
            this.startBall = startBall;
            this.pathBalls = pathBalls;
            this.targetSquare = targetSquare;
            this.pathExists = targetSquare != null;
        }

        public Point getCurrentStartPoint() {
            return new Point(startBall.x, startBall.y);
        }

        public List<Point> getCurrentPath() {
            if (pathBalls == null) return Collections.emptyList();

            List<Point> currentPath = new ArrayList<>();
            for (Ball ball : pathBalls) {
                currentPath.add(new Point(ball.x, ball.y));
            }
            return currentPath;
        }

        public void updatePath(List<Ball> allBalls, List<Barrier> barriers) {
            if (pathBalls != null && targetSquare != null) {
                if (pathBalls.size() > 0 &&
                        (startBall.distance(pathBalls.get(0)) > MAX_CONNECTION_DISTANCE ||
                                lineIntersectsBarrier(new Point(startBall.x, startBall.y),
                                        new Point(pathBalls.get(0).x, pathBalls.get(0).y),
                                        barriers))) {
                    pathExists = false;
                    return;
                }

                for (int i = 0; i < pathBalls.size() - 1; i++) {
                    if (pathBalls.get(i).distance(pathBalls.get(i+1)) > MAX_CONNECTION_DISTANCE ||
                            lineIntersectsBarrier(new Point(pathBalls.get(i).x, pathBalls.get(i).y),
                                    new Point(pathBalls.get(i+1).x, pathBalls.get(i+1).y),
                                    barriers)) {
                        pathExists = false;
                        return;
                    }
                }
                if (pathBalls.size() > 0 &&
                        (pathBalls.get(pathBalls.size()-1).distance(targetSquare) > MAX_CONNECTION_DISTANCE ||
                                lineIntersectsBarrier(new Point(pathBalls.get(pathBalls.size()-1).x,
                                                pathBalls.get(pathBalls.size()-1).y),
                                        targetSquare,
                                        barriers))) {
                    pathExists = false;
                    return;
                }
            }
            pathExists = targetSquare != null;
        }

        private boolean lineIntersectsBarrier(Point p1, Point p2, List<Barrier> barriers) {
            for (Barrier barrier : barriers) {
                if (lineIntersectsRectangle(p1, p2, barrier)) {
                    return true;
                }
            }
            return false;
        }

        private boolean lineIntersectsRectangle(Point p1, Point p2, Barrier rect) {
            return lineIntersectsLine(p1, p2, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y)) ||
                    lineIntersectsLine(p1, p2, new Point(rect.x + rect.width, rect.y), new Point(rect.x + rect.width, rect.y + rect.height)) ||
                    lineIntersectsLine(p1, p2, new Point(rect.x + rect.width, rect.y + rect.height), new Point(rect.x, rect.y + rect.height)) ||
                    lineIntersectsLine(p1, p2, new Point(rect.x, rect.y + rect.height), new Point(rect.x, rect.y)) ||
                    (rect.contains(p1) || rect.contains(p2));
        }

        private boolean lineIntersectsLine(Point p1, Point p2, Point p3, Point p4) {
            int d1 = direction(p3, p4, p1);
            int d2 = direction(p3, p4, p2);
            int d3 = direction(p1, p2, p3);
            int d4 = direction(p1, p2, p4);

            if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
                    ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
                return true;
            }

            if (d1 == 0 && onSegment(p3, p4, p1)) return true;
            if (d2 == 0 && onSegment(p3, p4, p2)) return true;
            if (d3 == 0 && onSegment(p1, p2, p3)) return true;
            if (d4 == 0 && onSegment(p1, p2, p4)) return true;

            return false;
        }

        private int direction(Point a, Point b, Point c) {
            return (b.x - a.x)*(c.y - a.y) - (b.y - a.y)*(c.x - a.x);
        }

        private boolean onSegment(Point a, Point b, Point c) {
            return Math.min(a.x, b.x) <= c.x && c.x <= Math.max(a.x, b.x) &&
                    Math.min(a.y, b.y) <= c.y && c.y <= Math.max(a.y, b.y);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Djakstra game = new Djakstra();
            game.setVisible(true);
        });
    }
}