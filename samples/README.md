# Samples

Only **public, non-personal** test streams belong here.

`demo.m3u` contains public HLS test streams (Mux, Apple, Sintel). It exists to
test the parser and the player without exposing a real IPTV subscription.

Rules:

- Never put a real playlist URL, username, password, token, or provider host in
  this directory.
- Real playlists live in `local/`, which is git-ignored.
- If a bug only reproduces with a real playlist, reduce it to a minimal fixture
  with fake credentials before adding it under `samples/` or a test resource.
