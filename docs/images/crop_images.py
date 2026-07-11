from pathlib import Path
from PIL import Image, ImageOps

WIDTH = 620
HEIGHT = 1300

input_dir = Path(".")
output_dir = input_dir / "output"
output_dir.mkdir(exist_ok=True)

extensions = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}

for file in input_dir.iterdir():
    if file.is_file() and file.suffix.lower() in extensions:
        try:
            with Image.open(file) as image:
                image = ImageOps.exif_transpose(image)

                # 按比例缩放并居中裁剪，不会拉伸
                result = ImageOps.fit(
                    image,
                    (WIDTH, HEIGHT),
                    method=Image.Resampling.LANCZOS,
                    centering=(0.5, 0.5),
                )

                output_file = output_dir / file.name

                # JPG 不支持透明通道
                if output_file.suffix.lower() in {".jpg", ".jpeg"}:
                    result = result.convert("RGB")
                    result.save(output_file, quality=95)
                else:
                    result.save(output_file)

                print(f"完成：{file.name}")

        except Exception as error:
            print(f"处理失败：{file.name}，原因：{error}")

print("全部处理完成，图片已保存到 output 文件夹。")