
import os
from PIL import Image

# Config
SOURCE_IMAGE_PATH = "/home/hemge/.gemini/antigravity/brain/edb857c2-121d-487d-b34a-cf14c2f0f9be/cardio_logo_digital_nodes_1768392990705.png"
RES_DIR = "app/src/main/res"

# Densities and sizes
# (Folder Suffix, Scale Factor) -> (Foreground Size, Legacy Size)
# MDPI (1x) -> Fg: 108, Leg: 48
# HDPI (1.5x) -> Fg: 162, Leg: 72
# XHDPI (2x) -> Fg: 216, Leg: 96
# XXHDPI (3x) -> Fg: 324, Leg: 144
# XXXHDPI (4x) -> Fg: 432, Leg: 192

DENSITIES = [
    ("mdpi", 108, 48),
    ("hdpi", 162, 72),
    ("xhdpi", 216, 96),
    ("xxhdpi", 324, 144),
    ("xxxhdpi", 432, 192)
]

def add_padding(img, padding_ratio=0.15):
    """
    Adds white padding around the image to safely fit in adaptive icon mask.
    The content (heart) is likely large. We want it effectively smaller in the frame.
    """
    w, h = img.size
    new_w = int(w * (1 + 2 * padding_ratio))
    new_h = int(h * (1 + 2 * padding_ratio))
    
    # Create new white image
    new_img = Image.new("RGBA", (new_w, new_h), (255, 255, 255, 255))
    
    # Paste old image in center
    offset_x = (new_w - w) // 2
    offset_y = (new_h - h) // 2
    new_img.paste(img, (offset_x, offset_y))
    
    return new_img

def main():
    if not os.path.exists(SOURCE_IMAGE_PATH):
        print(f"Error: Source image not found at {SOURCE_IMAGE_PATH}")
        return

    print(f"Processing icon from {SOURCE_IMAGE_PATH}")
    original_img = Image.open(SOURCE_IMAGE_PATH).convert("RGBA")
    
    # Current generated AI images often have subject filling the frame.
    # For adaptive icons, safe zone is center 66dp of 108dp (~61%).
    # If the heart is > 61% of width, it might be clipped by circle.
    # Let's add some padding to be safe.
    padded_img = add_padding(original_img, padding_ratio=0.25)

    for density, fg_size, legacy_size in DENSITIES:
        folder = os.path.join(RES_DIR, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        
        # 1. Adaptive Foreground (ic_launcher_foreground.png)
        # Resize padded image to fg_size
        fg_img = padded_img.resize((fg_size, fg_size), Image.Resampling.LANCZOS)
        fg_path = os.path.join(folder, "ic_launcher_foreground.png")
        fg_img.save(fg_path)
        print(f"Saved {fg_path} ({fg_size}x{fg_size})")
        
        # 2. Legacy Icon (ic_launcher.png)
        # Legacy icons don't need as much padding, but consistency is good.
        # Maybe use less padding? Or just use same relative visual.
        # Let's use the padded one but resized to legacy_size
        leg_img = padded_img.resize((legacy_size, legacy_size), Image.Resampling.LANCZOS)
        leg_path = os.path.join(folder, "ic_launcher.png")
        leg_img.save(leg_path)
        
        # 3. Round Icon (ic_launcher_round.png)
        round_path = os.path.join(folder, "ic_launcher_round.png")
        leg_img.save(round_path) # Same image, launcher usually handle round mask in XML or system handles it.
                                 # Actually Pixel phones use ic_launcher_round if provided.
                                 # Since our source has white background, a round png would be a square with white corners...
                                 # Ideally we should transparent-out the corners.
                                 # But for now, standard square with white background is acceptable (it will look like a white circle if system masks it, or white square).
                                 # Given we are "preparing for play store" quickly, this is sufficient.
        
    print("Icon processing complete.")

if __name__ == "__main__":
    main()
