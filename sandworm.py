import time
import os
import sys
import math
import random
import argparse
import threading
from threading import Event

# ANSI Escape Codes
CLEAR_SCREEN = "\033[2J"
CURSOR_TOP_LEFT = "\033[H"
RESET = "\033[0m"
SAND_COLOR = "\033[33m"
WORM_COLOR = "\033[90m"
WORM_HEAD_COLOR = "\033[37m"
SAND_PARTICLE_COLOR = "\033[93m"
SPICE_COLOR = "\033[95m"
ALT_SCREEN_ON = "\033[?1049h"
ALT_SCREEN_OFF = "\033[?1049l"
HIDE_CURSOR = "\033[?25l"
SHOW_CURSOR = "\033[?25h"

def get_terminal_size():
    try:
        size = os.get_terminal_size()
        return size.columns, size.lines
    except OSError:
        return 80, 24

def generate_dune_layer(width, height, layer_config):
    layer = [['' for _ in range(width)] for _ in range(height)]
    h_offset = layer_config['y_base']
    amplitude = layer_config['amplitude']
    frequency = layer_config['frequency']
    phase = layer_config['phase']
    char = layer_config['char']
    fill_char = layer_config['fill_char']

    for x in range(width):
        y = int(h_offset + amplitude * math.sin(frequency * x + phase))
        if 0 <= y < height:
            layer[y][x] = char
            for fill_y in range(y + 1, height):
                layer[fill_y][x] = fill_char
    return layer

class Particle:
    def __init__(self, x, y, vx, vy, char, color, life):
        self.x, self.y = x, y
        self.vx, self.vy = vx, vy
        self.char, self.color = char, color
        self.life = life

    def update(self):
        self.life -= 1
        self.x += self.vx
        self.y += self.vy
        self.vy += 0.05 # Gravity

def draw_frame(width, height, worm_pos, bg_layers, fg_layers, particles, spice):
    char_grid = [[' ' for _ in range(width)] for _ in range(height)]
    color_grid = [[RESET for _ in range(width)] for _ in range(height)]
    
    # Spice (Stars)
    for x, y, char in spice:
        if 0 <= x < width and 0 <= y < height:
            char_grid[int(y)][int(x)] = char
            color_grid[int(y)][int(x)] = SPICE_COLOR

    # BG Layers
    for layer in bg_layers:
        for y in range(height):
            for x in range(width):
                if layer[y][x]:
                    char_grid[y][x] = layer[y][x]
                    color_grid[y][x] = SAND_COLOR
                    
    # Draw Worm
    for i, (wx, wy) in enumerate(worm_pos):
        if i == 0:
            for dy in range(-2, 3):
                for dx in range(-2, 3):
                    px, py = int(wx + dx), int(wy + dy)
                    if 0 <= px < width and 0 <= py < height:
                        dist = math.sqrt(dx*dx + dy*dy)
                        if dist < 1.5: char = ' '
                        elif dist < 2.5: char = 'v' if dy < 0 else '^'
                        else: char = 'X'
                        char_grid[py][px] = char
                        color_grid[py][px] = WORM_HEAD_COLOR
        else:
            thickness = max(1, int(4 * (1 - i/len(worm_pos))))
            for dy in range(-thickness, thickness + 1):
                for dx in range(-thickness, thickness + 1):
                    px, py = int(wx + dx), int(wy + dy)
                    if 0 <= px < width and 0 <= py < height:
                        if abs(dx) + abs(dy) <= thickness + 1:
                            char = '#' if abs(dx) + abs(dy) <= thickness else '+'
                            char_grid[py][px] = char
                            color_grid[py][px] = WORM_COLOR

    # Particles
    for p in particles:
        px, py = int(p.x), int(p.y)
        if 0 <= px < width and 0 <= py < height:
            char_grid[py][px] = p.char
            color_grid[py][px] = p.color

    # FG Layers
    for layer in fg_layers:
        for y in range(height):
            for x in range(width):
                if layer[y][x]:
                    char_grid[y][x] = layer[y][x]
                    color_grid[y][x] = SAND_COLOR

    output = [CURSOR_TOP_LEFT]
    last_color = None
    for y in range(height):
        for x in range(width):
            color = color_grid[y][x]
            if color != last_color:
                output.append(color)
                last_color = color
            output.append(char_grid[y][x])
        if y < height - 1:
            output.append("\n")
            
    sys.stdout.write("".join(output))
    sys.stdout.flush()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--test", action="store_true")
    args = parser.parse_args()

    size_state = {'width': 0, 'height': 0}
    size_state['width'], size_state['height'] = get_terminal_size()
    needs_redraw = Event()

    def monitor_resize():
        last_w, last_h = size_state['width'], size_state['height']
        while True:
            try:
                time.sleep(0.2)
                cur_w, cur_h = get_terminal_size()
                if cur_w != last_w or cur_h != last_h:
                    size_state['width'], size_state['height'] = cur_w, cur_h
                    needs_redraw.set()
                    last_w, last_h = cur_w, cur_h
            except:
                break

    resize_thread = threading.Thread(target=monitor_resize, daemon=True)
    resize_thread.start()

    def init_layers():
        width, height = size_state['width'], size_state['height']
        bg_configs = [
            {'y_base': height // 2 - 3, 'amplitude': 4, 'frequency': 0.04, 'phase': 0, 'char': '*', 'fill_char': '.'},
            {'y_base': height // 2 + 1, 'amplitude': 5, 'frequency': 0.07, 'phase': 2, 'char': '^', 'fill_char': '.'},
        ]
        fg_configs = [
            {'y_base': height // 2 + 7, 'amplitude': 6, 'frequency': 0.09, 'phase': 4, 'char': '_', 'fill_char': ','},
        ]
        bg_layers = [generate_dune_layer(width, height, c) for c in bg_configs]
        fg_layers = [generate_dune_layer(width, height, c) for c in fg_configs]
        return bg_layers, fg_layers

    bg_layers, fg_layers = init_layers()
    
    worm_length = 40
    worm_segments = [(-100, -100) for _ in range(worm_length)]
    particles = []
    width, height = size_state['width'], size_state['height']
    spice = [(random.randint(0, width-1), random.randint(0, height-1), random.choice(['.', '·', '*'])) for _ in range(60)]
    
    t = 0
    max_frames = 100 if args.test else float('inf')
    frame_count = 0
    
    try:
        sys.stdout.write(ALT_SCREEN_ON + HIDE_CURSOR + CLEAR_SCREEN)
        sys.stdout.flush()

        while frame_count < max_frames:
            if needs_redraw.is_set():
                needs_redraw.clear()
                sys.stdout.write(CLEAR_SCREEN)
                sys.stdout.flush()
                bg_layers, fg_layers = init_layers()
                worm_segments = [(-100, -100) for _ in range(worm_length)]
                particles = []
                t = 0

            t += 1
            width, height = size_state['width'], size_state['height']
            head_x = (t % (width + worm_length)) - worm_length
            head_y = int(height // 2 + 5 + 9 * math.sin(0.07 * head_x))

            new_head = (head_x, head_y)
            worm_segments = [new_head] + worm_segments[:-1]

            # Sand spray when emerging or diving
            # Surface is roughly height // 2 + 5
            if abs(head_y - (height // 2 + 5)) < 3:
                for _ in range(5):
                    particles.append(Particle(head_x, head_y,
                                              random.uniform(-1, 1), random.uniform(-0.5, -1.5),
                                              random.choice(['.', ':', 'o']), SAND_PARTICLE_COLOR, random.randint(10, 20)))

            for p in particles[:]:
                p.update()
                if p.life <= 0: particles.remove(p)

            draw_frame(width, height, worm_segments, bg_layers, fg_layers, particles, spice)
            time.sleep(0.05)
            frame_count += 1

    except KeyboardInterrupt:
        pass
    finally:
        sys.stdout.write(ALT_SCREEN_OFF + SHOW_CURSOR + RESET)
        sys.stdout.flush()
        print("The spice must flow.")

if __name__ == "__main__":
    main()
