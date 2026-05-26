package net.turtton.starj

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class StarjApplication

fun main(args: Array<String>) {
    runApplication<StarjApplication>(*args)
}
