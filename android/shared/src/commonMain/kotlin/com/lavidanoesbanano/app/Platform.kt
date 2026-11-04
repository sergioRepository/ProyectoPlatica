package com.lavidanoesbanano.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform