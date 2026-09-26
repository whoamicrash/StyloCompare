[README (6).md](https://github.com/user-attachments/files/32684205/README.6.md)
# StyloCompare

![License](https://img.shields.io/badge/license-MIT-blue.svg) ![Java](https://img.shields.io/badge/Java-8%2B-orange.svg)

Compares two text files and estimates how likely it is that the same person wrote both. Works with any language.

## Run

Requires Java 8 or newer.

**Windows:** double-click `StyloCompare.jar` (or run `Run_StyloCompare.bat`).

**Linux / macOS:**

```bash
java -jar StyloCompare.jar
```

## Usage

1. Drop two text files into the window, or select them with the file picker.
2. Click **Compare**.
3. Save the report or copy it out. Text selection and Ctrl+C work as usual.

### Command line

```bash
java -jar StyloCompare.jar --cli text1.txt text2.txt --out report.txt
```

| Option | Description |
|--------|-------------|
| `--out FILE` | Write the report to a file |
| `--force` | Skip the confirmation prompt for short texts |
| `--selftest` | Run the built-in self-check |

## Reading the report

The verdict falls into one of five ranges, from "very likely the same author" to "very likely different authors". The P(same author) score is a weighted combination of 17 style metrics, adjusted for text length.

In the metrics table:

- `d` is the distance between the two texts (lower means more similar)
- `w` is the weight of the metric
- `(neutral)` marks metrics that are left out of the average

## Limitations

The result is an estimate, not proof. Topic, genre, quoted material, and machine-generated text can all skew the verdict. With texts shorter than 500 words accuracy drops, and the program will tell you when that happens.

## Building from source

**Windows:** run `build.bat`

**Linux / macOS:** run `./build.sh`

JDK 8 or newer is required. The sources are included in `StyloCompare_src.zip` and in this repository.

## License

MIT. Author: [@whoamicrash](https://github.com/whoamicrash).
