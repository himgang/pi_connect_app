#!/usr/bin/env bash
set -euo pipefail

gh run list --workflow "Android CI" --limit 5
