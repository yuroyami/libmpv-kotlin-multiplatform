package io.github.yuroyami.libmpv.buildtools

import java.io.File

/** The file of an unpacked tree that lists every file under its `lib/`, written by `buildscripts/package-tree.sh`. */
const val TREE_MANIFEST_NAME: String = "MANIFEST.txt"

/**
 * What is wrong with an unpacked static mpv tree: a file that [manifest] names but [tree] lacks, and any
 * shared library under `lib/`. A tree holds static archives only, so a shared one means a wrong build.
 */
fun treeProblems(tree: File, manifest: List<String>): List<String> {
    val missing = manifest.filter { it.isNotBlank() && !tree.resolve(it).isFile }.map { "$it is in the manifest but not in the tree" }
    val shared = tree.resolve("lib").walkTopDown()
        .filter { it.isFile && (it.extension in setOf("dylib", "dll") || ".so" in it.name) }
        .map { "${it.relativeTo(tree).path} is a shared library; a tree holds static archives only" }
    return missing + shared
}
