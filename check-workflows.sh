#!/bin/bash

echo "=== GitHub Workflow Monitoring Script ==="
echo "Repository: forcoder/msg-monitor"
echo "Branch: main"
echo ""

echo "=== Checking workflow files ==="
echo "1. CI Workflow (.github/workflows/ci.yml):"
echo "   - Fixed lint job to run actual lint commands"
echo "   - Added proper artifact uploads"
echo ""

echo "2. Enhanced CI Workflow (.github/workflows/enhanced-ci.yml):"
echo "   - Converted from documentation to executable workflow"
echo "   - Added lint, unit-tests, and build-validation jobs"
echo "   - Proper parallel execution and test reporting"
echo ""

echo "3. Build Debug Workflow (.github/workflows/build-debug.yml):"
echo "   - No changes needed, already properly configured"
echo ""

echo "4. Release Workflow (.github/workflows/release.yml):"
echo "   - No changes needed, already properly configured"
echo ""

echo "=== Project Fixes ==="
echo "1. build.gradle.kts:"
echo "   - Removed duplicate Room schema configuration"
echo "   - Fixed Room schema directory path"
echo ""

echo "2. Created missing assets/schemas directory for Room database"
echo ""

echo "3. Fixed .gitignore to not ignore .github/ directory"
echo ""

echo "=== Workflow Status ==="
echo "Attempting to check workflow status..."
echo ""

# Try to get workflow runs with timeout
timeout 10 gh run list --limit 5 || echo "Failed to get workflow runs (network timeout)"

echo ""
echo "=== Manual Steps Required ==="
echo "1. Push the changes to GitHub to trigger workflows:"
echo "   git push origin main"
echo ""
echo "2. Monitor the workflows at:"
echo "   https://github.com/forcoder/msg-monitor/actions"
echo ""
echo "3. If workflows fail, check the logs for specific errors"
echo "   and make additional fixes as needed."

# Check if workflows directory is properly committed
if [ -d ".github/workflows" ]; then
    echo ""
    echo "=== Local Workflow Files ==="
    ls -la .github/workflows/
fi