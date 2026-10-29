"""Synthesize restrained gem-box rummage and reveal Foley. Author: NingMeng123."""
from array import array
import hashlib
import json
import math
from pathlib import Path
import random
import re
import shutil
import subprocess
import sys
import wave

ROOT = Path(__file__).resolve().parents[1]
RATE = 44100
SEED = 19621029
OUTPUT = ROOT / 'src/main/resources/assets/modern_ite/sounds/item'
WORK = ROOT / 'build/verification/gem-box-synthetic-audio'
SOURCES = ROOT / 'tools/sound_sources'


def fade(t, duration, attack, release):
    def half_cosine(value):
        return (1 - math.cos(math.pi * max(0, min(1, value)))) / 2
    return half_cosine(t / attack) * half_cosine((duration - t) / release)


def rummage():
    duration = .42
    rng = random.Random(SEED)
    low = medium = high = 0.0
    coefficients = [1 - math.exp(-2 * math.pi * hz / RATE) for hz in [180, 1100, 3200]]
    samples = []
    for index in range(round(duration * RATE)):
        t = index / RATE
        white = rng.gauss(0, 1)
        low += coefficients[0] * (white - low)
        medium += coefficients[1] * (white - medium)
        high += coefficients[2] * (white - high)
        pulses = sum(strength * math.exp(-.5 * ((t - center) / width) ** 2)
                     for center, width, strength in [(.07, .035, .7), (.18, .05, 1), (.3, .042, .6)])
        sample = ((medium - low) + .13 * (high - medium)) * (.03 + pulses)
        for start, hz, decay, strength in [(.075, 320, .015, .05), (.215, 620, .012, .045), (.31, 280, .025, .04)]:
            elapsed = t - start
            if elapsed >= 0:
                sample += strength * math.sin(2 * math.pi * hz * elapsed) * math.exp(-elapsed / decay)
        samples.append(sample * fade(t, duration, .008, .035))
    return samples, {'duration_seconds': duration, 'peak_target_dbfs': -15,
                     'seed': SEED, 'noise_filter_hz': [180, 1100, 3200],
                     'rustle_pulses': [[.07, .035, .7], [.18, .05, 1], [.3, .042, .6]],
                     'contact_taps': [[.075, 320, .015, .05], [.215, 620, .012, .045], [.31, 280, .025, .04]],
                     'attack_seconds': .008, 'release_seconds': .035}


def reveal():
    duration = .72
    rng = random.Random(SEED + 1)
    partials = [(1, .42, .18), (2.01, .16, .11), (2.99, .055, .08), (4.2, .018, .045)]
    notes = [(0, 1318.510, 1), (.09, 1975.533, .22), (.17, 2637.020, .09)]
    samples = []
    for index in range(round(duration * RATE)):
        t = index / RATE
        sample = 0.0
        for start, frequency, strength in notes:
            elapsed = t - start
            if elapsed < 0:
                continue
            attack = (1 - math.exp(-elapsed / .0015))
            sample += strength * attack * sum(
                amplitude * math.sin(2 * math.pi * frequency * ratio * elapsed) * math.exp(-elapsed / decay)
                for ratio, amplitude, decay in partials)
        # Quiet contact transient adds a natural crystalline touch without a sharp click.
        sample += .012 * rng.gauss(0, 1) * math.exp(-t / .004)
        samples.append(sample * fade(t, duration, .003, .045))
    return samples, {'duration_seconds': duration, 'peak_target_dbfs': -11,
                     'seed': SEED + 1, 'notes_start_frequency_strength': notes,
                     'partials_ratio_amplitude_decay': partials, 'note_attack_seconds': .0015,
                     'contact_noise_strength': .012, 'contact_noise_decay_seconds': .004,
                     'attack_seconds': .003, 'release_seconds': .045}


def encode(name, samples, synthesis):
    peak = max(abs(value) for value in samples)
    target = 10 ** (synthesis['peak_target_dbfs'] / 20)
    pcm = array('h', (round(value / peak * target * 32767) for value in samples))
    if sys.byteorder != 'little':
        pcm.byteswap()
    raw = pcm.tobytes()
    wav = WORK / f'{name}.wav'
    with wave.open(str(wav), 'wb') as writer:
        writer.setnchannels(1)
        writer.setsampwidth(2)
        writer.setframerate(RATE)
        writer.writeframes(raw)
    output = OUTPUT / f'{name}.ogg'
    subprocess.run([shutil.which('ffmpeg'), '-hide_banner', '-loglevel', 'error', '-y', '-i', str(wav),
                    '-map_metadata', '-1', '-fflags', '+bitexact', '-flags:a', '+bitexact',
                    '-ac', '1', '-ar', str(RATE), '-c:a', 'libvorbis', '-q:a', '6',
                    '-metadata', 'comment=Original procedural Foley by NingMeng123', str(output)], check=True)
    decoded = subprocess.run([shutil.which('ffmpeg'), '-hide_banner', '-i', str(output),
                              '-af', 'astats=metadata=1:reset=0', '-f', 'null', '-'],
                             stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True, check=True).stderr
    decoded_peak = float(re.findall(r'Peak level dB: ([-\d.]+)', decoded)[-1])
    decoded_rms = float(re.findall(r'RMS level dB: ([-\d.]+)', decoded)[-1])
    assert decoded_peak < 0, f'{name} must retain clipping headroom'
    metadata = json.loads(subprocess.check_output([shutil.which('ffprobe'), '-v', 'error', '-show_entries',
                                                  'stream=codec_name,sample_rate,channels,duration', '-of', 'json', str(output)], text=True))
    stream = metadata['streams'][0]
    assert stream['codec_name'] == 'vorbis' and stream['channels'] == 1 and int(stream['sample_rate']) == RATE
    assert abs(float(stream['duration']) - len(samples) / RATE) < 1 / RATE
    report = {'author': 'NingMeng123', 'origin': 'Original procedural Foley; no external recording',
              'generator': 'tools/generate_gem_action_sounds.py', 'sample_rate': RATE,
              'synthesis': synthesis, 'pcm16_sha256': hashlib.sha256(raw).hexdigest(),
              'encoder': {'codec': 'libvorbis', 'quality': 6, 'bitexact': True},
              'output': {'path': output.relative_to(ROOT).as_posix(), 'bytes': output.stat().st_size,
                         'sha256': hashlib.sha256(output.read_bytes()).hexdigest(), 'channels': 1,
                         'samples': len(samples), 'duration_seconds': len(samples) / RATE,
                         'decoded_peak_dbfs': decoded_peak, 'decoded_rms_dbfs': decoded_rms}}
    (SOURCES / f'{name}.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'{name}: {len(samples) / RATE:.2f}s, mono, peak {decoded_peak:.2f}dBFS, RMS {decoded_rms:.2f}dBFS')


if __name__ == '__main__':
    if not shutil.which('ffmpeg') or not shutil.which('ffprobe'):
        raise SystemExit('ffmpeg and ffprobe are required on PATH.')
    for directory in [OUTPUT, WORK, SOURCES]:
        directory.mkdir(parents=True, exist_ok=True)
    encode('gem_box_rummage', *rummage())
    encode('gem_box_reveal', *reveal())
