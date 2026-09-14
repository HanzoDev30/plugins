import json
import urllib.request
import os
import sys

REPO_OWNER = "HanzoDev1375"
REPO_NAME = "ghostideplugins"
BRANCH = "main"
API_BASE = f"https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}/contents"
RAW_BASE = f"https://raw.githubusercontent.com/{REPO_OWNER}/{REPO_NAME}/{BRANCH}"
JSON_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "open.json")


def github_api(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Python"})
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())


def get_existing_names(data):
    return {item["name"] for item in data}


def main():
    if os.path.exists(JSON_PATH):
        with open(JSON_PATH, "r") as f:
            local_data = json.load(f)
    else:
        local_data = []

    existing = get_existing_names(local_data)
    contents = github_api(API_BASE)

    added = []
    for item in contents:
        if item["type"] != "dir":
            continue
        folder_name = item["name"]
        if folder_name in existing:
            continue

        folder_contents = github_api(item["url"])
        files = {f["name"] for f in folder_contents}

        icon_file = None
        gpl_file = None
        plugin_dir = None
        doc_exists = "doc.json" in files

        for f in folder_contents:
            if f["name"].endswith(".png") and f["type"] == "file":
                icon_file = f["name"]
            if f["name"].endswith(".gpl") and f["type"] == "file":
                gpl_file = f["name"]
            if f["type"] == "dir" and not f["name"].startswith("."):
                plugin_dir = f["name"]

        if not (icon_file and doc_exists and gpl_file and plugin_dir):
            print(f"[SKIP] {folder_name} - missing required files")
            continue

        new_entry = {
            "name": folder_name,
            "icon": f"{RAW_BASE}/{folder_name}/{icon_file}",
            "doc": f"{RAW_BASE}/{folder_name}/doc.json",
            "gplfile": f"{RAW_BASE}/{folder_name}/{gpl_file}",
            "source": f"https://github.com/{REPO_OWNER}/{REPO_NAME}/tree/{BRANCH}/{folder_name}/{plugin_dir}",
        }
        local_data.append(new_entry)
        added.append(folder_name)

    with open(JSON_PATH, "w") as f:
        json.dump(local_data, f, indent=2)

    if added:
        print(f"[OK] Added: {', '.join(added)}")
    else:
        print("[OK] No new plugins found")


if __name__ == "__main__":
    main()
