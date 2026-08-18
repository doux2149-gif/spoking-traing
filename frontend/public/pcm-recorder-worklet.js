const TARGET_SAMPLE_RATE = 16000
const FRAME_SAMPLES = 640

class PcmRecorderProcessor extends AudioWorkletProcessor {
  constructor() {
    super()
    this.pending = []
    this.position = 0
    this.ratio = sampleRate / TARGET_SAMPLE_RATE
  }

  process(inputs) {
    const input = inputs[0]?.[0]
    if (!input)
      return true

    const samples = []
    while (this.position < input.length) {
      const lower = Math.floor(this.position)
      const upper = Math.min(lower + 1, input.length - 1)
      const weight = this.position - lower
      samples.push(input[lower] * (1 - weight) + input[upper] * weight)
      this.position += this.ratio
    }
    this.position -= input.length

    for (const sample of samples)
      this.pending.push(Math.max(-1, Math.min(1, sample)))

    while (this.pending.length >= FRAME_SAMPLES) {
      const frame = this.pending.splice(0, FRAME_SAMPLES)
      const pcm = new Int16Array(FRAME_SAMPLES)
      for (let index = 0; index < frame.length; index += 1)
        pcm[index] = frame[index] < 0 ? frame[index] * 0x8000 : frame[index] * 0x7FFF
      this.port.postMessage(pcm.buffer, [pcm.buffer])
    }
    return true
  }
}

registerProcessor('pcm-recorder-processor', PcmRecorderProcessor)
