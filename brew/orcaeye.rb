cask "orcaeye" do
  arch arm: "arm64", intel: "x64"

  version "0.1.0"
  sha256 arm:   "0000000000000000000000000000000000000000000000000000000000000000",
         intel: "1111111111111111111111111111111111111111111111111111111111111111"

  url "https://github.com/SimonSchubert/Orcaeye/releases/download/v#{version}/Orcaeye-#{version}-macos-#{arch}.dmg",
      verified: "github.com/SimonSchubert/Orcaeye/"
  name "Orcaeye"
  desc "Browse agent skills, memories and config for Claude, Grok and OpenCode"
  homepage "https://github.com/SimonSchubert/Orcaeye"

  livecheck do
    url :url
    strategy :github_latest
  end

  depends_on macos: :big_sur

  app "Orcaeye.app"
end
