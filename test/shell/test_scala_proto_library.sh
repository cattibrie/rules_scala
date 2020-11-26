# shellcheck source=./test_runner.sh
dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
. "${dir}"/test_runner.sh
. "${dir}"/test_helper.sh
runner=$(get_test_runner "${1:-local}")

test_scala_proto_library_action_label() {
  cd third_party/test/proto
  local expected="action 'scala //:proto'"
  local action=$(bazel aquery --include_aspects 'mnemonic("Scalac", //...)' | grep ^action | head -n 1)
  if [ "$action" != "$expected" ]; then
    echo "Expected \"$expected\" but got \"$action\""
    exit 1
  fi
}

export USE_BAZEL_VERSION=${USE_BAZEL_VERSION:-$(cat ../../../.bazelversion)}
$runner test_scala_proto_library_action_label
