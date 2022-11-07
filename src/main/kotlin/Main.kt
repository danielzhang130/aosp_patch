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

    val manifest = readManifest(aospDirProperty!!)
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
                println("$name not found")
                return@forEach
            }

            val projectDir = File(Paths.get(project.path).toAbsolutePath().toString())
            if (!projectDir.exists()) {
                println("$projectDir does not exist")
                return@forEach
            }

            branch(projectDir, "patch-$time")
            applyPatch(projectDir, it)
        }
    } else if (args.size == 3 && args[0] == "format") {
        val projectDir = File(Paths.get(args[1]).toAbsolutePath().toString())
        assert(projectDir.exists())

        val project = manifest.findByDir(projectDir)
        if (project == null) {
            println("${args[1]} not a project")
            return
        }

        val sinceCommit = args[2]

        formatPatch(projectDir, sinceCommit, File(patchDir, project.name.replace("/", "_") + ".patch"))
    }
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

private fun Manifest.findByDir(projectDir: File): Project? {
    val pwd = File("")
    var cur = projectDir
    while (cur.startsWith(pwd.absoluteFile)) {
        val relative = cur.toRelativeString(pwd.absoluteFile)
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