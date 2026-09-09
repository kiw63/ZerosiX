package com.zerosix.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SoundEngine(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val key = pool.load(context, com.zerosix.app.R.raw.key, 1)
    private val tick = pool.load(context, com.zerosix.app.R.raw.boot_tick, 1)
    private val confirm = pool.load(context, com.zerosix.app.R.raw.confirm, 1)
    private val startup = pool.load(context, com.zerosix.app.R.raw.startup, 1)
    fun key() { pool.play(key, .45f, .45f, 1, 0, 1f) }
    fun tick() { pool.play(tick, .55f, .55f, 1, 0, 1f) }
    fun confirm() { pool.play(confirm, .6f, .6f, 1, 0, 1f) }
    fun startup() { pool.play(startup, .55f, .55f, 1, 0, 1f) }
    fun release() { pool.release() }
}
