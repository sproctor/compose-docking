package com.seanproctor.docking.tao

/**
 * Marks the `docking-tao` API: docking in Nucleus's Tao windows.
 *
 * Nucleus 3.0, the only Nucleus with a Tao backend, is published as dev builds alone, and
 * this module is built on parts of it that Nucleus itself marks experimental - the platform
 * drag-and-drop session that carries drags on native Wayland. The API here exposes Nucleus
 * types directly ([dev.nucleusframework.application.NucleusApplicationScope],
 * [dev.nucleusframework.application.NucleusDecoratedWindowScope],
 * [dev.nucleusframework.application.NucleusWindow]), so it changes when they do, and may
 * change or be removed in any release until Nucleus 3.0 is stable.
 *
 * Opt in with `@OptIn(ExperimentalDockingTaoApi::class)`, or module-wide with the
 * `-opt-in=com.seanproctor.docking.tao.ExperimentalDockingTaoApi` compiler argument.
 */
@RequiresOptIn(
    message = "docking-tao follows Nucleus 3.0 dev builds and may change or be removed in any release. " +
        "Opt in with @OptIn(com.seanproctor.docking.tao.ExperimentalDockingTaoApi::class).",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class ExperimentalDockingTaoApi
