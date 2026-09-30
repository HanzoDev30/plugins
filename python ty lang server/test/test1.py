from dataclasses import dataclass


@dataclass
class User:
    name: str
    age: int


def greet(user: User) -> str:
    return f"hello {user.name}"


def broken(user: User) -> str:
    return user.nmae


def add(a: int, b: int) -> int:
    return a + b


def also_broken() -> int:
    return add("1", 2)


if __name__ == "__main__":
    print(greet(User("ghost", 20)))
    print(add(1, 2))
