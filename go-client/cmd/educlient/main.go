package main

import (
	"github.com/LittleChiu/EduClient/go-client/internal/jwgl"
	"github.com/LittleChiu/EduClient/go-client/internal/ui"
)

func main() {
	ui.New(jwgl.NewClient()).Run()
}
