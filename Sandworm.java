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
    private static final String ALT_SCREEN_ON = "\033[?1049h";
    private static final String ALT_SCREEN_OFF = "\033[?1049l";
    private static final String HIDE_CURSOR = "\033[?25l";
    private static final String SHOW_CURSOR = "\033[?25h";

    private static volatile int width = 80;
    private static volatile int height = 24;
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
            Process p = new ProcessBuilder("sh", "-c", "stty size < /dev/tty").start();
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            var size = reader.readLine();
            if (size != null) {
                var parts = size.trim().split("\\s+");
                if (parts.length >= 2) {
                    return new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[0])};
                }
            }
        } catch (Exception ignored) {}

        try {
            var cols = System.getenv("COLUMNS");
            var lines = System.getenv("LINES");
            if (cols != null && lines != null) {
                return new int[]{Integer.parseInt(cols), Integer.parseInt(lines)};
            }
        } catch (Exception ignored) {}

        return new int[]{width, height};
    }

    private static String[][] generateDuneLayer(DuneConfig config, int w, int h) {
        var layer = new String[h][w];
        for (var x = 0; x < w; x++) {
            var y = (int) (config.yBase + config.amplitude * Math.sin(config.frequency * x + config.phase));
            if (y >= 0 && y < h) {
                layer[y][x] = String.valueOf(config.symbol);
                for (var fillY = y + 1; fillY < h; fillY++) {
                    layer[fillY][x] = String.valueOf(config.fillChar);
                }
            }
        }
        return layer;
    }

    private static void drawFrame(List<int[]> wormPos, List<String[][]> bgLayers, List<String[][]> fgLayers, List<Particle> particles, List<double[]> spice) {
        int h = height;
        int w = width;
        var charGrid = new char[h][w];
        var colorGrid = new String[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                charGrid[y][x] = ' ';
                colorGrid[y][x] = RESET;
            }
        }

        // 1. BG Layers
        for (var layer : bgLayers) {
            if (layer.length != h || (layer.length > 0 && layer[0].length != w)) continue;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (layer[y][x] != null) {
                        charGrid[y][x] = layer[y][x].charAt(0);
                        colorGrid[y][x] = SAND_COLOR;
                    }
                }
            }
        }

        // 2. Worm
        for (int i = 0; i < wormPos.size(); i++) {
            var pos = wormPos.get(i);
            int wx = pos[0], wy = pos[1];

            if (i == 0) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        int px = wx + dx, py = wy + dy;
                        if (px >= 0 && px < w && py >= 0 && py < h) {
                            double dist = Math.sqrt(dx * dx + dy * dy);
                            if (dist < 1.5) charGrid[py][px] = ' ';
                            else if (dist < 2.5) charGrid[py][px] = (dy < 0) ? 'v' : '^';
                            else charGrid[py][px] = 'X';
                            colorGrid[py][px] = WORM_HEAD_COLOR;
                        }
                    }
                }
            } else {
                int thickness = Math.max(1, (int) (4 * (1 - (double) i / wormPos.size())));
                for (int dy = -thickness; dy <= thickness; dy++) {
                    for (int dx = -thickness; dx <= thickness; dx++) {
                        int px = wx + dx, py = wy + dy;
                        if (px >= 0 && px < w && py >= 0 && py < h) {
                            if (Math.abs(dx) + Math.abs(dy) <= thickness + 1) {
                                charGrid[py][px] = (Math.abs(dx) + Math.abs(dy) <= thickness) ? '#' : '+';
                                colorGrid[py][px] = WORM_COLOR;
                            }
                        }
                    }
                }
            }
        }

        // 3. Particles
        for (var p : particles) {
            int px = (int) p.x, py = (int) p.y;
            if (px >= 0 && px < w && py >= 0 && py < h) {
                charGrid[py][px] = p.charStr.charAt(0);
                colorGrid[py][px] = p.color;
            }
        }

        // 4. FG Layers
        for (var layer : fgLayers) {
            if (layer.length != h || (layer.length > 0 && layer[0].length != w)) continue;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (layer[y][x] != null) {
                        charGrid[y][x] = layer[y][x].charAt(0);
                        colorGrid[y][x] = SAND_COLOR;
                    }
                }
            }
        }

        // 5. Spice (Stars) - Draw last and only in empty spaces to prevent flickering
        for (var s : spice) {
            int sx = (int) s[0], sy = (int) s[1];
            if (sx >= 0 && sx < w && sy >= 0 && sy < h) {
                if (charGrid[sy][sx] == ' ') {
                    charGrid[sy][sx] = (char) s[2];
                    colorGrid[sy][sx] = SPICE_COLOR;
                }
            }
        }

        var out = new StringBuilder(h * w * 4);
        out.append(CURSOR_TOP_LEFT);
        for (int y = 0; y < h; y++) {
            String lastColor = "";
            for (int x = 0; x < w; x++) {
                String color = colorGrid[y][x];
                if (!color.equals(lastColor)) {
                    out.append(color);
                    lastColor = color;
                }
                out.append(charGrid[y][x]);
            }
            if (y < h - 1) out.append("\n"); // Avoid newline on the last line to prevent scrolling
        }
        System.out.print(out.toString());
        System.out.flush();
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
        var spice = new ArrayList<double[]>();
        var spiceChars = new char[]{'.', '·', '*'};

        var regenerateLayers = new Runnable() {
            public void run() {
                int w = width;
                int h = height;
                bgConfigs.clear();
                bgConfigs.add(new DuneConfig(h / 2 - 3, 4, 0.04, 0, '*', '.'));
                bgConfigs.add(new DuneConfig(h / 2 + 1, 5, 0.07, 2, '^', '.'));
                fgConfigs.clear();
                fgConfigs.add(new DuneConfig(h / 2 + 7, 6, 0.09, 4, '_', ','));
                bgLayers.clear();
                for (var c : bgConfigs) bgLayers.add(generateDuneLayer(c, w, h));
                fgLayers.clear();
                for (var c : fgConfigs) fgLayers.add(generateDuneLayer(c, w, h));

                spice.clear();
                for (var i = 0; i < 60; i++) {
                    spice.add(new double[]{random.nextInt(w), random.nextInt(h), spiceChars[random.nextInt(3)]});
                }
            }
        };
        regenerateLayers.run();

        var wormLength = 40;
        var wormSegments = new ArrayList<int[]>();
        for (var i = 0; i < wormLength; i++) wormSegments.add(new int[]{-100, -100});

        var particles = new ArrayList<Particle>();
        var frameCount = 0;
        var maxFrames = testMode ? 100 : Integer.MAX_VALUE;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.print(ALT_SCREEN_OFF + SHOW_CURSOR + RESET + CLEAR_SCREEN);
            System.out.println("The spice must flow.");
            System.out.flush();
        }));

        System.out.print(ALT_SCREEN_ON + HIDE_CURSOR + CLEAR_SCREEN);
        System.out.flush();
        var t = 0;
        while (frameCount < maxFrames) {
            if (needsRedraw.getAndSet(false)) {
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
