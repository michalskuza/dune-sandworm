import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class Sandworm {
    // ANSI Escape Codes
    private static final String CLEAR_SCREEN = "\033[2J";
    private static final String CURSOR_TOP_LEFT = "\033[H";
    private static final String RESET = "\033[0m";
    private static final String SAND_COLOR = "\033[33m";
    private static final String WORM_COLOR = "\033[90m";
    private static final String WORM_HEAD_COLOR = "\033[37m";
    private static final String SAND_PARTICLE_COLOR = "\033[93m";
    private static final String SPICE_COLOR = "\033[95m";

    private static int width = 80;
    private static int height = 24;
    private static final Random random = new Random();
    private static final AtomicBoolean needsRedraw = new AtomicBoolean(false);

    static class Particle {
        double x, y, vx, vy;
        String charStr, color;
        int life;

        Particle(double x, double y, double vx, double vy, String charStr, String color, int life) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.charStr = charStr; this.color = color; this.life = life;
        }

        void update() {
            life--;
            x += vx;
            y += vy;
            vy += 0.05; // Gravity
        }
    }

    static class DuneConfig {
        int yBase;
        double amplitude, frequency, phase;
        char symbol, fillChar;

        DuneConfig(int yBase, double amplitude, double frequency, double phase, char symbol, char fillChar) {
            this.yBase = yBase; this.amplitude = amplitude; this.frequency = frequency;
            this.phase = phase; this.symbol = symbol; this.fillChar = fillChar;
        }
    }

    private static int[] getTerminalSize() {
        try {
            var cols = System.getenv("COLUMNS");
            var lines = System.getenv("LINES");
            int w = width, h = height;
            if (cols != null) w = Integer.parseInt(cols);
            if (lines != null) h = Integer.parseInt(lines);

            if (cols == null || lines == null) {
                Process p = new ProcessBuilder("sh", "-c", "stty size < /dev/tty").start();
                var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                var size = reader.readLine();
                if (size != null) {
                    var parts = size.trim().split("\\s+");
                    if (parts.length >= 2) {
                        h = Integer.parseInt(parts[0]);
                        w = Integer.parseInt(parts[1]);
                    }
                }
                p.waitFor();
            }
            return new int[]{w, h};
        } catch (Exception ignored) {
            return new int[]{width, height};
        }
    }

    private static String[][] generateDuneLayer(DuneConfig config) {
        var layer = new String[height][width];
        for (var x = 0; x < width; x++) {
            var y = (int) (config.yBase + config.amplitude * Math.sin(config.frequency * x + config.phase));
            if (y >= 0 && y < height) {
                layer[y][x] = String.valueOf(config.symbol);
                for (var fillY = y + 1; fillY < height; fillY++) {
                    layer[fillY][x] = String.valueOf(config.fillChar);
                }
            }
        }
        return layer;
    }

    private static void drawFrame(List<int[]> wormPos, List<String[][]> bgLayers, List<String[][]> fgLayers, List<Particle> particles, List<double[]> spice) {
        var grid = new String[height][width];
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                grid[y][x] = " ";
            }
        }

        // 0. Spice
        for (var s : spice) {
            var sx = (int) s[0];
            var sy = (int) s[1];
            if (sx >= 0 && sx < width && sy >= 0 && sy < height) {
                if (random.nextDouble() > 0.05) {
                    grid[sy][sx] = SPICE_COLOR + (char) s[2] + RESET;
                }
            }
        }

        // 1. BG Layers
        for (var layer : bgLayers) {
            for (var y = 0; y < height; y++) {
                for (var x = 0; x < width; x++) {
                    if (layer[y][x] != null) grid[y][x] = layer[y][x];
                }
            }
        }

        // 2. Worm
        for (var i = 0; i < wormPos.size(); i++) {
            var pos = wormPos.get(i);
            var wx = pos[0];
            var wy = pos[1];

            if (i == 0) {
                for (var dy = -2; dy <= 2; dy++) {
                    for (var dx = -2; dx <= 2; dx++) {
                        var px = wx + dx;
                        var py = wy + dy;
                        if (px >= 0 && px < width && py >= 0 && py < height) {
                            var dist = Math.sqrt(dx * dx + dy * dy);
                            char c;
                            if (dist < 1.5) c = ' ';
                            else if (dist < 2.5) c = (dy < 0) ? 'v' : '^';
                            else c = 'X';
                            grid[py][px] = WORM_HEAD_COLOR + c + RESET;
                        }
                    }
                }
            } else {
                var thickness = Math.max(1, (int) (4 * (1 - (double) i / wormPos.size())));
                for (var dy = -thickness; dy <= thickness; dy++) {
                    for (var dx = -thickness; dx <= thickness; dx++) {
                        var px = wx + dx;
                        var py = wy + dy;
                        if (px >= 0 && px < width && py >= 0 && py < height) {
                            if (Math.abs(dx) + Math.abs(dy) <= thickness + 1) {
                                var c = (Math.abs(dx) + Math.abs(dy) <= thickness) ? '#' : '+';
                                grid[py][px] = WORM_COLOR + c + RESET;
                            }
                        }
                    }
                }
            }
        }

        // 3. Particles
        for (var p : particles) {
            var px = (int) p.x;
            var py = (int) p.y;
            if (px >= 0 && px < width && py >= 0 && py < height) {
                grid[py][px] = p.color + p.charStr + RESET;
            }
        }

        // 4. FG Layers
        for (var layer : fgLayers) {
            for (var y = 0; y < height; y++) {
                for (var x = 0; x < width; x++) {
                    if (layer[y][x] != null) grid[y][x] = layer[y][x];
                }
            }
        }

        var out = new StringBuilder(CURSOR_TOP_LEFT);
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var cell = grid[y][x];
                if (cell.length() == 1 && "^_*.,".indexOf(cell.charAt(0)) != -1) {
                    out.append(SAND_COLOR).append(cell).append(RESET);
                } else {
                    out.append(cell);
                }
            }
            out.append("\n");
        }
        System.out.print(out.toString());
    }

    public static void main(String[] args) throws InterruptedException {
        var testMode = false;
        for (var arg : args) {
            if (arg.equals("--test")) testMode = true;
        }

        // Get initial terminal size
        var size = getTerminalSize();
        width = size[0];
        height = size[1];

        // Start monitor thread for terminal resize
        var resizeMonitor = new Thread(() -> {
            var lastW = width;
            var lastH = height;
            while (true) {
                try {
                    Thread.sleep(200);
                    var curSize = getTerminalSize();
                    if (curSize[0] != lastW || curSize[1] != lastH) {
                        width = curSize[0];
                        height = curSize[1];
                        needsRedraw.set(true);
                        lastW = width;
                        lastH = height;
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        resizeMonitor.setDaemon(true);
        resizeMonitor.start();

        var bgConfigs = new ArrayList<DuneConfig>();
        var fgConfigs = new ArrayList<DuneConfig>();
        var bgLayers = new ArrayList<String[][]>();
        var fgLayers = new ArrayList<String[][]>();

        var regenerateLayers = new Runnable() {
            public void run() {
                bgConfigs.clear();
                bgConfigs.add(new DuneConfig(height / 2 - 3, 4, 0.04, 0, '*', '.'));
                bgConfigs.add(new DuneConfig(height / 2 + 1, 5, 0.07, 2, '^', '.'));
                fgConfigs.clear();
                fgConfigs.add(new DuneConfig(height / 2 + 7, 6, 0.09, 4, '_', ','));
                bgLayers.clear();
                for (var c : bgConfigs) bgLayers.add(generateDuneLayer(c));
                fgLayers.clear();
                for (var c : fgConfigs) fgLayers.add(generateDuneLayer(c));
            }
        };
        regenerateLayers.run();

        var wormLength = 40;
        var wormSegments = new ArrayList<int[]>();
        for (var i = 0; i < wormLength; i++) wormSegments.add(new int[]{-100, -100});

        var spice = new ArrayList<double[]>();
        var spiceChars = new char[]{'.', '·', '*'};
        for (var i = 0; i < 60; i++) {
            spice.add(new double[]{random.nextInt(width), random.nextInt(height), spiceChars[random.nextInt(3)]});
        }

        var particles = new ArrayList<Particle>();
        var frameCount = 0;
        var maxFrames = testMode ? 100 : Integer.MAX_VALUE;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.print(RESET + CLEAR_SCREEN + CURSOR_TOP_LEFT);
            System.out.println("The spice must flow.");
        }));

        System.out.print(CLEAR_SCREEN);
        var t = 0;
        while (frameCount < maxFrames) {
            if (needsRedraw.getAndSet(false)) {
                System.out.print(CLEAR_SCREEN);
                regenerateLayers.run();
                wormSegments.clear();
                for (var i = 0; i < wormLength; i++) wormSegments.add(new int[]{-100, -100});
                particles.clear();
                t = 0;
            }

            t++;
            var headX = (t % (width + wormLength)) - wormLength;
            var headY = (int) (height / 2 + 5 + 9 * Math.sin(0.07 * headX));

            wormSegments.add(0, new int[]{headX, headY});
            if (wormSegments.size() > wormLength) wormSegments.remove(wormSegments.size() - 1);

            if (Math.abs(headY - (height / 2 + 5)) < 3) {
                var pChars = new String[]{".", ":", "o"};
                for (var i = 0; i < 5; i++) {
                    particles.add(new Particle(headX, headY, random.nextDouble() * 2 - 1, random.nextDouble() * -1 - 0.5, pChars[random.nextInt(3)], SAND_PARTICLE_COLOR, random.nextInt(10) + 10));
                }
            }

            for (var i = particles.size() - 1; i >= 0; i--) {
                var p = particles.get(i);
                p.update();
                if (p.life <= 0) particles.remove(i);
            }

            drawFrame(wormSegments, bgLayers, fgLayers, particles, spice);
            Thread.sleep(50);
            frameCount++;
        }
        
        if (testMode) {
            System.exit(0);
        }
    }
}
