package alfred.jenkins

import alfred.jenkins.fixtures.SystemSpecModule
import cats.effect.IO
import cats.implicits._
import io.circe.syntax._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class SystemSpec extends AnyFlatSpec with Matchers {

  behavior of "Browse Command"

  it should "Support fetching and drilling" in withModule { m =>
    import m._
    for {
      rootResult <- CommandDispatcher.dispatch(BrowseArgs(path = None))
      _ <- IO {
        withClue(rootResult.asJson.spaces2SortKeys) {
          val titles = rootResult.items.map(_.title)
          titles must contain("Test 1")
          titles must contain("Test 2")
          titles must contain("Test 3")
          titles.size mustBe 3
        }
      }
      drillItem = rootResult.items.find(_.title == "Test 1").get

      drillResult <- CommandDispatcher.dispatch(BrowseArgs(path = Some(drillItem.variables("path"))))

      _ <- IO {
        withClue(drillResult.asJson.spaces2SortKeys) {
          drillResult.items.size mustBe 11
        }
      }
    } yield ()
  }

  it should "Support drilling into build history" in withModule { m =>
    import m._
    for {
      jobResult <- CommandDispatcher.dispatch(BrowseArgs(path = Some("https://jenkins.com/job/Test1")))
      childWithBuilds = jobResult.items.find(_.title == "Test1-templates-deploy").get
      _ <- IO {
        childWithBuilds.variables("action") mustBe "build-history"
      }

      jobBuildHistory <- CommandDispatcher.dispatch(BuildHistoryArgs(path = childWithBuilds.variables("path")))
      _ <- IO {
        withClue(jobBuildHistory.asJson.spaces2SortKeys) {
          jobBuildHistory.items.size mustBe 20
          val titles = jobBuildHistory.items.map(_.title)
          (620 to 639).foreach { i =>
            titles must contain(s"#$i")
          }
        }
      }
    } yield ()
  }

  behavior of "Search Command"

  it should "List only leaf jobs" in withModule { m =>
    import m._
    for {
      result <- CommandDispatcher.dispatch(SearchArgs(path = None))
      _ <- IO {
        withClue(result.asJson.spaces2SortKeys) {
          val titles = result.items.map(_.title)
          titles must contain("Test1-templates-deploy")
          titles must contain("coordinator-deploy")
          titles must contain("eli-test-delete-acks")
          titles must contain("nightly-tests")
          titles must contain("user-mamagement-group-sync")
          titles must contain("Test3-ADS")
          titles.size mustBe 6

          result.items.forall(_.variables("action") == "build-history") mustBe true
        }
      }
    } yield ()
  }

  behavior of "Login Command"

  it should "Store the base url, username and password" in withModule { m =>
    import m._
    for {
      _ <- CommandDispatcher.dispatch(
        LoginArgs(
          url = "https://jenkins.com",
          username = "eli-jordan",
          password = "my-super-secret-pwd"
        ))
      pwd      <- CredentialService.read(JenkinsAccount("eli-jordan"))
      settings <- Settings.fetch
      _ <- IO {
        pwd mustBe "my-super-secret-pwd"
        settings.url mustBe "https://jenkins.com"
        settings.username mustBe "eli-jordan"
      }
    } yield ()
  }

  private def withModule(action: SystemSpecModule => IO[Unit]): Unit = {
    SystemSpecModule.resource.use(action).unsafeRunSync()
  }
}
