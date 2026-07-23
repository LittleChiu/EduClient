package ui

import "testing"

func TestGroupBatchTargetsPreservesPriority(t *testing.T) {
	lines := []string{
		"(2026-2027-1)-00322-01",
		"(2026-2027-1)-00322-09",
		"(2026-2027-1)-01401-01",
	}
	targets := groupBatchTargets(lines)
	defer func() {
		for _, target := range targets {
			target.cancel()
		}
	}()
	if len(targets) != 2 {
		t.Fatalf("got %d groups, want 2", len(targets))
	}
	if targets[0].name != "00322" {
		t.Fatalf("first group = %q, want 00322", targets[0].name)
	}
	if len(targets[0].candidates) != 2 || targets[0].candidates[0] != lines[0] || targets[0].candidates[1] != lines[1] {
		t.Fatalf("priority order changed: %#v", targets[0].candidates)
	}
}

func TestFullResponseDetection(t *testing.T) {
	for _, body := range []string{`{"flag":"0","msg":"教学班已满"}`, `{"message":"余量不足"}`, "course is full"} {
		if !isFullResponse(body) {
			t.Fatalf("did not detect full response: %s", body)
		}
	}
	if isFullResponse(`{"flag":"0","msg":"暂未开放"}`) {
		t.Fatal("unrelated response detected as full")
	}
}
