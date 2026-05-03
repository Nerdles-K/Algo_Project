def compute_video_pagerank(user_videos, all_videos, alpha=0.85, max_iter=50, tol=1e-6):
    videos = list(all_videos)
    n = len(videos)

    if n == 0:
        return {}

    pr = {video: 1.0 / n for video in videos}

    for _ in range(max_iter):
        new_pr = {video: (1 - alpha) / n for video in videos}

        for user, watched_videos in user_videos.items():
            if not watched_videos:
                continue

            share = 1.0 / len(watched_videos)

            for video in watched_videos:
                new_pr[video] += alpha * share

        total = sum(new_pr.values())
        if total > 0:
            new_pr = {k: v / total for k, v in new_pr.items()}

        diff = sum(abs(new_pr[v] - pr[v]) for v in videos)
        pr = new_pr

        if diff < tol:
            break

    return pr