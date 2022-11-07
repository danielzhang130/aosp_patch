import org.simpleframework.xml.Attribute
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

@Root(strict = false, name = "manifest")
class Manifest {
    @field:ElementList(inline = true)
    lateinit var projects: List<Project>
}

@Root(strict = false)
class Project {
    @field:Attribute
    lateinit var path: String
    @field:Attribute
    lateinit var name: String
}
