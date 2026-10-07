# Sandworm Terminal Animation

A high-fidelity terminal animation of the Great Worm of Arrakis, written in Java.

```text
                                        ____
                                     .-"    "-.
                                    /  v v v  \
                                   |  v  X  v  |
                                    \  ^ ^ ^  /
                                     '._   _.'
                                    /   '--'   \
                                   /  _..---.._  \
                                  /  /         \  \
                                 |  |           |  |
                                  \  \         /  /
                                   \  '-------'  /
                                 /  _..-------.._  \
                                |  /             \  |
                                | |               | |
                                |  \             /  |
                                 \  '-----------'  /
                             _.-'                 '-._
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
       ~    ~    ~  .  ~    ~    ~    ~    ~  .  ~    ~    ~    ~
```

## Features

- **Layered Dunes:** Three distinct dune layers with varied character sets (`^`, `_`, `*`) and shading to provide a rich sense of depth.
- **Detailed Worm Model:** A multi-character body that tapers toward the tail, featuring a distinct maw with visible teeth.
- **Dynamic Particles:** Sand displacement effects (`.`, `:`, `o`) that kick up as the worm breaches and falls with simulated gravity.
- **Atmospheric Spice:** Flickering spice particles (`.`, `·`, `*`) rendered in a distinct purplish-pink hue.
- **ANSI Color Support:** Optimized for modern terminals with full color support for the sands, the worm, and the spice.

## Requirements

### Java Version
- **Java 10 or higher:** The source code utilizes `var` for local variable type inference.

### Python Version
- **Python 3.6 or higher**

## How to Run

### Java Version
1. **Compile the source:**
   ```bash
   javac Sandworm.java
   ```
2. **Run the animation:**
   ```bash
   java Sandworm
   ```

### Python Version
1. **Run the script:**
   ```bash
   python3 sandworm.py
   ```

## Controls

- **Stop:** Press `Ctrl+C` to halt the animation. The terminal will be cleared, and a final message will be displayed: *"The spice must flow."*

---
*The spice must flow.*
