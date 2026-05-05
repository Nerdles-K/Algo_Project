def compute_lcc(target_user, user_friends):
    neighbors = list(user_friends.get(target_user, []))
    k = len(neighbors)

    if k < 2:
        return 0.0

    e = 0
    for i in range(k):
        for j in range(i + 1, k):
            u = neighbors[i]
            v = neighbors[j]
            if v in user_friends.get(u, set()):
                e += 1

    lcc = (2 * e) / (k * (k - 1))
    return round(lcc, 4)