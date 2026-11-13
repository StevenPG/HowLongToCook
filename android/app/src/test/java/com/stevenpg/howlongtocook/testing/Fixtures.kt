package com.stevenpg.howlongtocook.testing

import com.stevenpg.howlongtocook.data.model.CookingMethod
import com.stevenpg.howlongtocook.data.model.MethodType
import com.stevenpg.howlongtocook.data.model.Product
import com.stevenpg.howlongtocook.data.model.Step
import com.stevenpg.howlongtocook.data.model.StepAction
import java.io.File

/** Deterministic UUIDs from short names, so tests stay readable. */
fun uuid(name: String): String = java.util.UUID.nameUUIDFromBytes(name.toByteArray()).toString()

fun product(id: String, updatedAt: Long, name: String = id, barcode: String? = null) = Product(
    id = uuid(id),
    name = name,
    barcodes = listOfNotNull(barcode),
    methods = listOf(CookingMethod(MethodType.MICROWAVE, power = "High", steps = listOf(Step(StepAction.COOK, 180)))),
    createdAt = 1,
    updatedAt = updatedAt,
)

/** Reads a file shared with the server tests (repo-root/api/examples). */
fun sharedExample(name: String): String {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
        val candidate = File(dir, "api/examples/$name")
        if (candidate.exists()) return candidate.readText()
        dir = dir.parentFile
    }
    error("api/examples/$name not found")
}
