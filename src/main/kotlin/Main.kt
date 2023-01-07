import org.simpleframework.xml.core.Persister
import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

fun main(args: Array<String>) {
    var hasAssert = false
    try {
        assert(false)
    } catch (e: AssertionError) {
        hasAssert = true
    }

    if (!hasAssert) {
        throw IllegalStateException("Enable JVM options -ea")
    }

    val home = System.getProperty("user.home")
    val propertiesFile = File("$home/.config/aosp_patch.properties")
    if (!propertiesFile.exists()) {
        println("initializing config")
        assert(propertiesFile.parentFile.exists())
        assert(propertiesFile.createNewFile())
        val template = ClassLoader.getSystemResource("aosp_patch.properties").readText()
        propertiesFile.writeText(template)
        return
    }
    val properties = Properties()
    FileInputStream(propertiesFile).use { properties.load(it) }
    val aospDirProperty = (properties["aosp_dir"] as? String)?.replace("~", home)
    val patchDirProperty = (properties["patch_dir"] as? String)?.replace("~", home)
    assert(!aospDirProperty.isNullOrBlank())
    assert(!patchDirProperty.isNullOrBlank())

    properties.remove("aosp_dir")
    properties.remove("patch_dir")

    val manifest = readManifest(aospDirProperty!!)
    loadCustomManifest(properties, manifest)
    val patchDir = File(Paths.get(patchDirProperty!!).toAbsolutePath().toString())
    assert(patchDir.exists())

    if (args.size == 1 && args[0] == "apply") {
        val time = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now())
            .replace(":", "-")

        patchDir.listFiles()?.forEach {
            if (it.isDirectory) {
                return@forEach
            }

            val name = it.nameWithoutExtension
            val project = manifest.findByName(name)
            if (project == null) {
                System.err.println("$name not found")
                return@forEach
            }

            val projectDir = File(Paths.get(project.path).toAbsolutePath().toString())
            if (!projectDir.exists()) {
                System.err.println("$projectDir does not exist")
                return@forEach
            }

            if (!branchExists(projectDir, "patch-base")) {
                branch(projectDir, "patch-base")
                if (!branchExists(projectDir, "patch-base")) {
                    System.err.println("branch patch-base does not exist")
                    return@forEach
                }
            } else {
                moveBranch(projectDir, "patch-base", "HEAD")
            }

            branch(projectDir, "patch-$time")
            if (!branchExists(projectDir, "patch-$time")) {
                System.err.println("branch patch-$time does not exist")
                return@forEach
            }
            applyPatch(projectDir, it)
        }
    } else if (args.size >= 2 && args[0] == "format") {
        val projectDir = File(Paths.get(args[1]).toAbsolutePath().toString())
        assert(projectDir.exists())

        val project = manifest.findByDir(aospDirProperty, projectDir)
        if (project == null) {
            System.err.println("${args[1]} not a project")
            return
        }

        val sinceCommit = if (args.size == 3) args[2] else "patch-base"

        formatPatch(projectDir, sinceCommit, File(patchDir, project.name.replace("/", "_") + ".patch"))
    }
}

fun loadCustomManifest(properties: Properties, manifest: Manifest) {
    manifest.projects = properties.map {
        Project().apply {
            name = it.key.toString()
            path = it.value.toString()
        }
    } + manifest.projects
}

fun branchExists(projectDir: File, branch: String): Boolean {
    val command = "git show-ref --verify --quiet refs/heads/$branch"
    return ProcessBuilder(command.split(" "))
        .directory(projectDir)
        .inheritIO()
        .start()
        .run {
            waitFor()
            exitValue() == 0
        }
}

fun moveBranch(projectDir: File, branch: String, ref: String) {
    val command = "git branch -f $branch $ref"
    ProcessBuilder(command.split(" "))
        .directory(projectDir)
        .inheritIO()
        .start()
        .waitFor()
}

fun branch(projectDir: File, branch: String) {
    val command = "git checkout -b $branch"
    println(command)
    ProcessBuilder(command.split(" "))
        .directory(projectDir)
        .inheritIO()
        .start()
        .waitFor()
}

fun formatPatch(projectDir: File, sinceCommit: String, patchFile: File) {
    val command = "git format-patch $sinceCommit --stdout"
    println(command)
    ProcessBuilder(command.split(" "))
        .directory(projectDir)
        .redirectOutput(patchFile)
        .start()
        .waitFor()
}

fun applyPatch(projectDir: File, patch: File) {
    val command = "git am $patch"
    println(command)
    ProcessBuilder(command.split(" "))
        .directory(projectDir)
        .inheritIO()
        .start()
        .waitFor()
}

private fun Manifest.findByDir(baseDir: String, projectDir: File): Project? {
    val base = File(baseDir)
    var cur = projectDir
    while (cur.startsWith(base.absoluteFile)) {
        val relative = cur.toRelativeString(base.absoluteFile)
        val project = projects.find { relative == it.path }
        if (project != null) {
            return project
        }
        cur = cur.parentFile
    }
    return null
}

private fun Manifest.findByName(name: String) = projects.find { name == it.name.replace('/', '_') }

private fun readManifest(aospDir: String): Manifest {
    val file = File(aospDir, ".repo/manifests/default.xml")
    assert(file.exists())
    val string = file.readText()
    return Persister().read(Manifest::class.java, string)
}
