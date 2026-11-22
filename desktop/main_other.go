//go:build !windows

package main

import "os"

func replaceStateFile(source, destination string) error {
	return os.Rename(source, destination)
}
