def rank_videos(candidate_videos, pagerank_scores, alpha=0.6, beta=0.4):
    ranked = []

    for video, distance in candidate_videos.items():
        distance_score = 1.0 / distance
        popularity_score = pagerank_scores.get(video, 0.0)
        final_score = alpha * distance_score + beta * popularity_score

        ranked.append({
            "video": video,
            "distance": distance,
            "distance_score": round(distance_score, 4),
            "pagerank_score": round(popularity_score, 6),
            "final_score": round(final_score, 6)
        })

    ranked.sort(key=lambda x: x["final_score"], reverse=True)
    return ranked