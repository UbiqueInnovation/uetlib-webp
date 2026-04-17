# Benchmark plan

## Goals

- Quantify throughput and latency gains from in-process/native approach.
- Compare against current CLI + temp-file flow.
- Focus on animated WebP workloads first, then still images.

## Scenarios

1. Still encode from raw RGBA.
2. Animation encode from 24-120 raw RGBA frames.
3. Optional future: compressed JPEG bytes decode+encode path.

## Metrics

- Throughput (ops/s)
- p50/p95 encode latency
- output size
- CPU utilization
- GC pressure (allocation rate, pause time)

## Corpora

- Synthetic deterministic RGBA frames (for reproducibility)
- Real production-like corpora:
  - short social-style animations
  - high-resolution bursts
  - alpha-heavy overlays

## Baselines

- Native fast path (this project)
- Existing CLI + temp-file path

## Controls

- Fixed encoder settings per run (`quality`, `method`, `lossless`, animation tuning)
- Warm-up before measurement
- Isolated CPU pinning where possible

## Tooling

- JMH benchmark class in `src/jmh/java`
- Optional perf sampling (`perf`, `async-profiler`) during heavy animation runs
