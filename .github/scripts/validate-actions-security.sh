#!/bin/bash
# GitHub Actions Security Validation Script
# このスクリプトは、サードパーティアクションがコミットハッシュで固定されているかチェックします

set -e

WORKFLOW_DIR=".github/workflows"
VIOLATIONS_FOUND=false

echo "🔍 GitHub Actionsワークフローのセキュリティチェックを開始します..."

if [ ! -d "$WORKFLOW_DIR" ]; then
    echo "❌ $WORKFLOW_DIR ディレクトリが見つかりません"
    exit 1
fi

# ワークフローファイルを検索
for workflow_file in "$WORKFLOW_DIR"/*.yml "$WORKFLOW_DIR"/*.yaml; do
    if [ ! -f "$workflow_file" ]; then
        continue
    fi
    
    echo "📄 チェック中: $workflow_file"
    
    # サードパーティアクションでタグやブランチ指定を検出
    while IFS= read -r line; do
        # usesを含む行で、コミットハッシュ（40桁の16進数）でない@指定を検出
        if echo "$line" | grep -E "^\s*uses:" > /dev/null; then
            # @の後が40桁の16進数でない場合は問題あり
            if echo "$line" | grep -E "@[^#]*" | grep -v -E "@[a-f0-9]{40}(\s|$|#)" > /dev/null; then
                echo "⚠️  潜在的なセキュリティ問題を発見: $(echo "$line" | sed 's/^[[:space:]]*//')"
                echo "   → ファイル: $workflow_file"
                VIOLATIONS_FOUND=true
            fi
        fi
    done < "$workflow_file"
done

if [ "$VIOLATIONS_FOUND" = true ]; then
    echo ""
    echo "❌ セキュリティ違反が発見されました。"
    echo "💡 修正方法:"
    echo "   - サードパーティアクションはコミットハッシュで固定してください"
    echo "   - 例: uses: actions/checkout@692973e3d937129bcbf40652eb9f2f61becf3332 # v4.1.7"
    echo "   - 詳細は .github/GITHUB_ACTIONS_SECURITY.md を参照してください"
    exit 1
else
    echo ""
    echo "✅ すべてのワークフローファイルでセキュリティ要件を満たしています。"
fi