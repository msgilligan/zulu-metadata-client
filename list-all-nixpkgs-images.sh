#!/bin/bash
binary[0]="24 linux-glibc x64 false"
binary[1]="24 linux-glibc x64 true"
binary[2]="24 linux-glibc aarch64 false"
binary[3]="24 linux-glibc aarch64 true"
binary[4]="24 macos x64 false"
binary[5]="24 macos x64 true"
binary[6]="24 macos aarch64 false"
binary[7]="24 macos aarch64 true"
for package in "${binary[@]}"; do
    ./ZuluQuery.java $package
done
