load(
    "//scala:scala_cross_version.bzl",
    _default_maven_server_urls = "default_maven_server_urls",
)
load("//third_party/repositories:repositories.bzl", "repositories")

def specs2_version():
    return "4.4.1"

def specs2_repositories(
        maven_servers = _default_maven_server_urls(),
        overriden_artifacts = {}):
    repositories(
        for_artifact_ids = [
            "io_bazel_rules_scala_org_specs2_specs2_common",
            "io_bazel_rules_scala_org_specs2_specs2_core",
            "io_bazel_rules_scala_org_specs2_specs2_fp",
            "io_bazel_rules_scala_org_specs2_specs2_matcher",
        ],
        maven_servers = maven_servers,
        fetch_sources = True,
        overriden_artifacts = overriden_artifacts,
    )

    native.bind(
        name = "io_bazel_rules_scala/dependency/specs2/specs2",
        actual = "@io_bazel_rules_scala//specs2:specs2",
    )

def specs2_dependencies():
    return ["//external:io_bazel_rules_scala/dependency/specs2/specs2"]
