#!/bin/bash

echo "=== GitHub Actions Workflow Monitor ==="
echo "Started at: $(date)"
echo ""

# Function to check workflow status using curl with timeout
check_workflow() {
    echo "Checking for active workflows..."

    # Try direct curl with short timeout
    response=$(curl -s --connect-timeout 5 \
                 --max-time 10 \
                 https://api.github.com/repos/forcoder/msg-monitor/actions/workflows/enhanced-ci.yml/runs?per_page=1)

    if [[ $? -eq 0 ]]; then
        run_id=$(echo "$response" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
        status=$(echo "$response" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)

        if [[ -n "$run_id" ]]; then
            echo "✓ Found workflow run ID: $run_id"
            echo "Status: $status"

            if [[ "$status" == "completed" ]]; then
                conclusion=$(echo "$response" | grep -o '"conclusion":"[^"]*"' | head -1 | cut -d'"' -f4)
                echo "Conclusion: $conclusion"
                return 0
            elif [[ "$status" == "failure" ]] || [[ "$status" == "cancelled" ]]; then
                echo "Workflow failed or was cancelled"
                return 1
            else
                echo "Workflow still running: $status"
                return 2
            fi
        else
            echo "No active workflow found yet"
            return 3
        fi
    else
        echo "API call failed, trying alternative method..."
        return 4
    fi
}

# Wait for workflow to start
echo "Waiting for GitHub Actions to trigger (workflow should start within 30 seconds)..."
sleep 30

# Check multiple times
attempts=0
max_attempts=15

while [[ $attempts -lt $max_attempts ]]; do
    attempts=$((attempts + 1))
    echo ""
    echo "=== Attempt $attempts of $max_attempts ($(date)) ==="

    check_workflow
    result=$?

    case $result in
        0)  # Success
            echo ""
            echo "🎉 BUILD COMPLETED SUCCESSFULLY!"
            exit 0
            ;;
        1)  # Failure
            echo ""
            echo "❌ BUILD FAILED"
            echo "Check GitHub Actions manually at: https://github.com/forcoder/msg-monitor/actions"
            exit 1
            ;;
        2)  # Still running
            echo "Still waiting... ($attempts/$max_attempts)"
            sleep 60
            ;;
        3|4)  # No workflow yet or API error
            echo "Still waiting for workflow to start..."
            sleep 60
            ;;
    esac
done

echo ""
echo "⚠️  Monitoring timeout reached"
echo "The workflow may still be running. Please check:"
echo "   https://github.com/forcoder/msg-monitor/actions"
exit 1