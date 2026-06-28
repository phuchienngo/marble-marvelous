package com.phuchienngo.marblemarvelous.input

import com.phuchienngo.marblemarvelous.di.WallpaperEngineScope
import javax.inject.Inject

@WallpaperEngineScope
class InputMultiplexer
    @Inject
    constructor() : InputProcessor {
        private val processors: MutableList<InputProcessor> = ArrayList(4)

        constructor(vararg processors: InputProcessor) : this() {
            this.processors.addAll(processors.toList())
        }

        fun addProcessor(
            index: Int,
            processor: InputProcessor,
        ) {
            this.processors.add(index, processor)
        }

        fun removeProcessor(index: Int) {
            this.processors.removeAt(index)
        }

        fun addProcessor(processor: InputProcessor) {
            this.processors.add(processor)
        }

        fun removeProcessor(processor: InputProcessor) {
            this.processors.remove(processor)
        }

        fun size(): Int = processors.size

        fun clear() = processors.clear()

        override fun touchDown(
            i: Int,
            i2: Int,
            i3: Int,
        ): Boolean {
            for (p in processors) if (p.touchDown(i, i2, i3)) return true
            return false
        }

        override fun touchUp(
            i: Int,
            i2: Int,
            i3: Int,
        ): Boolean {
            for (p in processors) if (p.touchUp(i, i2, i3)) return true
            return false
        }

        override fun touchDragged(
            i: Int,
            i2: Int,
            i3: Int,
        ): Boolean {
            for (p in processors) if (p.touchDragged(i, i2, i3)) return true
            return false
        }
    }
